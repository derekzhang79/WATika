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
import java.util.Arrays;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.multiple.AbstractMultipleParser.MetadataPolicy;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Runs the input stream through all available parsers,
 *  merging the metadata from them based on the
 *  {@link MetadataPolicy} chosen.
 *
 * Warning - currently only one Parser should output
 *  any Content to the {@link ContentHandler}, the rest
 *  should only output {@link Metadata}. A solution to
 *  multiple-content is still being worked on...
 *
 * @since Apache Tika 1.18
 */
public class SupplementingParser extends AbstractMultipleParser {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 313179254565350994L;

    /**
     * The different Metadata Policies we support (not discard)
     */
    public static final List<MetadataPolicy> allowedPolicies =
            Arrays.asList(MetadataPolicy.FIRST_WINS,
                    MetadataPolicy.LAST_WINS,
                    MetadataPolicy.KEEP_ALL);


    public SupplementingParser(MediaTypeRegistry registry, MetadataPolicy policy,
                               Parser... parsers) {
        this(registry, policy, Arrays.asList(parsers));
    }
    public SupplementingParser(MediaTypeRegistry registry, MetadataPolicy policy,
                               List<Parser> parsers) {
        super(registry, policy, parsers);
        // TODO Check the policy is one we support
    }

    @Override
    protected boolean parserCompleted(Parser parser, Metadata metadata,
                                      ContentHandler handler, Exception exception) {
        // If there was no exception, just carry on to the next
        if (exception == null) return true;

        // Record the details of this exception in the metadata
        // TODO Share logic with the Recursive Parser Wrapper

        // Have the next parser tried
        return true;
    }
}