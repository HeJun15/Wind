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

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.elasticsearch.env.Environment;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexSettings;

import java.util.Set;

public class KeywordMarkerTokenFilterFactory extends AbstractTokenFilterFactory {

    private final CharArraySet keywordLookup;

    public KeywordMarkerTokenFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);

        boolean ignoreCase = settings.getAsBoolean("ignore_case", false);
        Set<?> rules = Analysis.getWordSet(env, settings, "keywords");
        if (rules == null) {
            throw new IllegalArgumentException("keyword filter requires either `keywords` or `keywords_path` to be configured");
        }
        keywordLookup = new CharArraySet(rules, ignoreCase);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new SetKeywordMarkerFilter(tokenStream, keywordLookup);
    }
}
