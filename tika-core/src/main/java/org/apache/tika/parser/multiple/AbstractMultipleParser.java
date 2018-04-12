/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.multiple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Abstract base class for parser wrappers which may / will
 *  process a given stream multiple times, merging the results
 *  of the various parsers used.
 * End users should normally use {@link FallbackParser} or
 *  {@link SupplementingParser} along with a Strategy.
 *
 * @since Apache Tika 1.18
 */
public abstract class AbstractMultipleParser extends AbstractParser {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 5383668090329836559L;

    /**
     * The various strategies for handling metadata emitted by
     *  multiple parsers.
     * Note that not all will be supported by all subclasses.
     */
    public enum MetadataPolicy {
        /**
         * Before moving onto another parser, throw away
         *  all previously seen metadata
         */
        DISCARD_ALL,
        /**
         * The first parser to output a given key wins,
         *  merge in non-clashing other keys
         */
        FIRST_WINS,
        /**
         * The last parser to output a given key wins,
         *  overriding previous parser values for a
         *  clashing key.
         */
        LAST_WINS,
        /**
         * Where multiple parsers output a given key,
         *  store all their different (unique) values
         */
        KEEP_ALL
    };

    // TODO Figure out some sort of Content Policy and how
    //  it might possibly work

    /**
     * Media type registry.
     */
    private MediaTypeRegistry registry;

    /**
     * How we should handle metadata clashes
     */
    private MetadataPolicy policy;

    /**
     * List of the multiple parsers to try.
     */
    private List<Parser> parsers;

    /**
     * Computed list of Mime Types to offer, which is all
     *  those in common between the parsers.
     * For explicit mimetypes only, use a {@link ParserDecorator}
     */
    private Set<MediaType> offeredTypes;

    // TODO Tika Config XML Support for these parsers and their
    //  metadata policies + parsers + mimetypes
    // See https://wiki.apache.org/tika/CompositeParserDiscussion

    /**
     * Returns the media type registry used to infer type relationships.
     *
     * @return media type registry
     */
    public MediaTypeRegistry getMediaTypeRegistry() {
        return registry;
    }

    /**
     * Sets the media type registry used to infer type relationships.
     *
     * @param registry media type registry
     */
    public void setMediaTypeRegistry(MediaTypeRegistry registry) {
        this.registry = registry;
    }


    public AbstractMultipleParser(MediaTypeRegistry registry, MetadataPolicy policy,
                                  Parser... parsers) {
        this(registry, policy, Arrays.asList(parsers));
    }
    public AbstractMultipleParser(MediaTypeRegistry registry, MetadataPolicy policy,
                                  List<Parser> parsers) {
        this.policy = policy;
        this.parsers = parsers;
        this.registry = registry;

        // TODO Only offer those in common to several/all parser
        // TODO Some sort of specialisation / subtype support
        this.offeredTypes = new HashSet<>();
        for (Parser parser : parsers) {
            offeredTypes.addAll(
                    parser.getSupportedTypes(new ParseContext())
            );
        }
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return offeredTypes;
    }

    /**
     * Used to notify implementations that a Parser has Finished
     *  or Failed, and to allow them to decide to continue or
     *  abort further parsing
     */
    protected abstract boolean parserCompleted(
            Parser parser, Metadata metadata,
            ContentHandler handler, Exception exception);

    /**
     * Delegates the call to one or more Parsers,
     * Delegates the call to the matching component parser.
     * <p>
     * Potential {@link RuntimeException}s, {@link IOException}s and
     * {@link SAXException}s unrelated to the given input stream and content
     * handler are automatically wrapped into {@link TikaException}s to better
     * honor the {@link Parser} contract.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        TemporaryResources tmp = new TemporaryResources();
        try {
            // Force the stream to be a Tika one
            // Force the stream to be file-backed, so we can
            //  re-wind it safely if required
            // TODO Support an InputStreamFactory as an alternative to
            //  Files, see TIKA-2585
            TikaInputStream taggedStream = TikaInputStream.get(stream, tmp);
            Path path = taggedStream.getPath();

            // TODO Somehow shield/wrap the Handler, so that we can
            //  avoid failures if multiple parsers want to do content
            // TODO Solve the multiple-content problem!

            for (Parser p : parsers) {
                // TODO What's the best way to reset each time?
                TikaInputStream parserStream = TikaInputStream.get(path);

                // Record this parser
                metadata.add("X-Parsed-By", getParserName(p));

                // TODO Handle metadata clashes based on the Policy

                // Process if possible
                Exception failure = null;
                try {
                    p.parse(parserStream, handler, metadata, context);
                } catch (Exception e) {
                    failure = e;
                }

                // Notify the implementation how it went
                boolean tryNext = parserCompleted(p, metadata, handler, failure);
                if (!tryNext) break;

                // TODO Handle metadata clashes based on the Policy
            }
        } finally {
            tmp.dispose();
        }
    }

    private String getParserName(Parser parser) {
        // TODO Share this logic with CompositeParser
        if (parser instanceof ParserDecorator){
            return ((ParserDecorator) parser).getWrappedParser().getClass().getName();
        } else {
            return parser.getClass().getName();
        }
    }
}

