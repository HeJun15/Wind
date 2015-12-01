/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.settings.loader;

import org.apache.lucene.util.IOUtils;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.io.FastStringReader;
import org.elasticsearch.common.io.stream.StreamInput;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Settings loader that loads (parses) the settings in a properties format.
 */
public class PropertiesSettingsLoader implements SettingsLoader {

    @Override
    public Map<String, String> load(String source) throws IOException {
        Properties props = new NoDuplicatesProperties();
        FastStringReader reader = new FastStringReader(source);
        try {
            props.load(reader);
            Map<String, String> result = new HashMap<>();
            for (Map.Entry entry : props.entrySet()) {
                result.put((String) entry.getKey(), (String) entry.getValue());
            }
            return result;
        } finally {
            IOUtils.closeWhileHandlingException(reader);
        }
    }

    @Override
    public Map<String, String> load(byte[] source) throws IOException {
        Properties props = new NoDuplicatesProperties();
        StreamInput stream = StreamInput.wrap(source);
        try {
            props.load(stream);
            Map<String, String> result = new HashMap<>();
            for (Map.Entry entry : props.entrySet()) {
                result.put((String) entry.getKey(), (String) entry.getValue());
            }
            return result;
        } finally {
            IOUtils.closeWhileHandlingException(stream);
        }
    }

    class NoDuplicatesProperties extends Properties {
        @Override
        public synchronized Object put(Object key, Object value) {
            Object previousValue = super.put(key, value);
            if (previousValue != null) {
                throw new ElasticsearchParseException("duplicate settings key [{}] found, previous value [{}], current value [{}]", key, previousValue, value);
            }
            return previousValue;
        }
    }
}
