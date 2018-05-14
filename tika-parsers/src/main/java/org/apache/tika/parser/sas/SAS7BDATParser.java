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
package org.apache.tika.parser.sas;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import com.epam.parso.SasFileProperties;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.epam.parso.Column;
import com.epam.parso.DataWriterUtil;
import com.epam.parso.SasFileReader;
import com.epam.parso.impl.SasFileReaderImpl;

/**
 * Processes the SAS7BDAT data columnar database file used by SAS and 
 *  other similar languages.
 */
public class SAS7BDATParser extends AbstractParser {
    private static final long serialVersionUID = -2775485539937983150L;

    private static final MediaType TYPE_SAS7BDAT =
            MediaType.application("x-sas-data");
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(TYPE_SAS7BDAT);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, TYPE_SAS7BDAT.toString());

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        SasFileReader sas = new SasFileReaderImpl(stream);
        SasFileProperties props = sas.getSasFileProperties();

        /**
         * Record the interesting parts of the file's metadata
         */
        metadata.set(TikaCoreProperties.TITLE, props.getName());
        metadata.set(TikaCoreProperties.CREATED, props.getDateCreated());
        metadata.set(TikaCoreProperties.MODIFIED, props.getDateModified());
        // Output as a table
        xhtml.element("h1", props.getName());
        xhtml.startElement("table");
        xhtml.newline();

        // Do the column headings
        xhtml.startElement("tr");
        for (Column c : sas.getColumns()) {
            xhtml.startElement("th", "title", c.getName());
            xhtml.characters(c.getLabel());
            xhtml.endElement("th");
        }
        xhtml.endElement("tr");
        xhtml.newline();

        // Process each row in turn
        Object[] row = null;
        while ((row = sas.readNext()) != null) {
            xhtml.startElement("tr");
            for (String val : DataWriterUtil.getRowValues(sas.getColumns(), row)) {
                xhtml.element("td", val);
            }
            xhtml.endElement("tr");
            xhtml.newline();
        }

        // Finish
        xhtml.endElement("table");
        xhtml.endDocument();
    }
}