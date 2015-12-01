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

import org.elasticsearch.Version;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.mapper.core.StringFieldMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom analyzer that is built out of a single {@link org.apache.lucene.analysis.Tokenizer} and a list
 * of {@link org.apache.lucene.analysis.TokenFilter}s.
 */
public class CustomAnalyzerProvider extends AbstractIndexAnalyzerProvider<CustomAnalyzer> {

    private final Settings analyzerSettings;

    private CustomAnalyzer customAnalyzer;

    public CustomAnalyzerProvider(IndexSettings indexSettings,
                                  String name, Settings settings) {
        super(indexSettings, name, settings);
        this.analyzerSettings = settings;
    }

    public void build(AnalysisService analysisService) {
        String tokenizerName = analyzerSettings.get("tokenizer");
        if (tokenizerName == null) {
            throw new IllegalArgumentException("Custom Analyzer [" + name() + "] must be configured with a tokenizer");
        }

        TokenizerFactory tokenizer = analysisService.tokenizer(tokenizerName);
        if (tokenizer == null) {
            throw new IllegalArgumentException("Custom Analyzer [" + name() + "] failed to find tokenizer under name [" + tokenizerName + "]");
        }

        List<CharFilterFactory> charFilters = new ArrayList<>();
        String[] charFilterNames = analyzerSettings.getAsArray("char_filter");
        for (String charFilterName : charFilterNames) {
            CharFilterFactory charFilter = analysisService.charFilter(charFilterName);
            if (charFilter == null) {
                throw new IllegalArgumentException("Custom Analyzer [" + name() + "] failed to find char_filter under name [" + charFilterName + "]");
            }
            charFilters.add(charFilter);
        }

        List<TokenFilterFactory> tokenFilters = new ArrayList<>();
        String[] tokenFilterNames = analyzerSettings.getAsArray("filter");
        for (String tokenFilterName : tokenFilterNames) {
            TokenFilterFactory tokenFilter = analysisService.tokenFilter(tokenFilterName);
            if (tokenFilter == null) {
                throw new IllegalArgumentException("Custom Analyzer [" + name() + "] failed to find filter under name [" + tokenFilterName + "]");
            }
            tokenFilters.add(tokenFilter);
        }

        int positionIncrementGap = StringFieldMapper.Defaults.positionIncrementGap(indexSettings.getIndexVersionCreated());

        if (analyzerSettings.getAsMap().containsKey("position_offset_gap")){
            if (indexSettings.getIndexVersionCreated().before(Version.V_2_0_0)){
                if (analyzerSettings.getAsMap().containsKey("position_increment_gap")){
                    throw new IllegalArgumentException("Custom Analyzer [" + name() +
                            "] defined both [position_offset_gap] and [position_increment_gap], use only [position_increment_gap]");
                }
                positionIncrementGap = analyzerSettings.getAsInt("position_offset_gap", positionIncrementGap);
            }else {
                throw new IllegalArgumentException("Option [position_offset_gap] in Custom Analyzer [" + name() +
                        "] has been renamed, please use [position_increment_gap] instead.");
            }
        }

        positionIncrementGap = analyzerSettings.getAsInt("position_increment_gap", positionIncrementGap);

        int offsetGap = analyzerSettings.getAsInt("offset_gap", -1);;
        this.customAnalyzer = new CustomAnalyzer(tokenizer,
                charFilters.toArray(new CharFilterFactory[charFilters.size()]),
                tokenFilters.toArray(new TokenFilterFactory[tokenFilters.size()]),
                positionIncrementGap,
                offsetGap
        );
    }

    @Override
    public CustomAnalyzer get() {
        return this.customAnalyzer;
    }
}
