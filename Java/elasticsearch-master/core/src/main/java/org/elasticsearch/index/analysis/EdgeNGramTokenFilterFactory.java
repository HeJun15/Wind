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
import org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter;
import org.apache.lucene.analysis.ngram.Lucene43EdgeNGramTokenFilter;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.reverse.ReverseStringFilter;
import org.apache.lucene.util.Version;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;


/**
 *
 */
public class EdgeNGramTokenFilterFactory extends AbstractTokenFilterFactory {

    private final int minGram;

    private final int maxGram;

    public static final int SIDE_FRONT = 1;
    public static final int SIDE_BACK = 2;
    private final int side;

    private org.elasticsearch.Version esVersion;

    public EdgeNGramTokenFilterFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, name, settings);
        this.minGram = settings.getAsInt("min_gram", NGramTokenFilter.DEFAULT_MIN_NGRAM_SIZE);
        this.maxGram = settings.getAsInt("max_gram", NGramTokenFilter.DEFAULT_MAX_NGRAM_SIZE);
        this.side = parseSide(settings.get("side", "front"));
        this.esVersion = org.elasticsearch.Version.indexCreated(indexSettings.getSettings());
    }
    
    static int parseSide(String side) {
        switch(side) {
            case "front": return SIDE_FRONT;
            case "back": return SIDE_BACK;
            default: throw new IllegalArgumentException("invalid side: " + side);
        }
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        TokenStream result = tokenStream;
        
        // side=BACK is not supported anymore but applying ReverseStringFilter up-front and after the token filter has the same effect
        if (side == SIDE_BACK) {
            result = new ReverseStringFilter(result);
        }
        
        if (version.onOrAfter(Version.LUCENE_4_3) && esVersion.onOrAfter(org.elasticsearch.Version.V_0_90_2)) {
            /*
             * We added this in 0.90.2 but 0.90.1 used LUCENE_43 already so we can not rely on the lucene version.
             * Yet if somebody uses 0.90.2 or higher with a prev. lucene version we should also use the deprecated version.
             */
            result = new EdgeNGramTokenFilter(result, minGram, maxGram);
        } else {
            result = new Lucene43EdgeNGramTokenFilter(result, minGram, maxGram);
        }
        
        // side=BACK is not supported anymore but applying ReverseStringFilter up-front and after the token filter has the same effect
        if (side == SIDE_BACK) {
            result = new ReverseStringFilter(result);
        }
        
        return result;
    }
}