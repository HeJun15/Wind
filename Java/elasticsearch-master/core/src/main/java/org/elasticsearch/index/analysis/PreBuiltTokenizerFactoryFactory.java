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
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.indices.analysis.PreBuiltTokenizers;

import java.io.IOException;

public class PreBuiltTokenizerFactoryFactory implements AnalysisModule.AnalysisProvider<TokenizerFactory> {

    private final TokenizerFactory tokenizerFactory;

    public PreBuiltTokenizerFactoryFactory(TokenizerFactory tokenizerFactory) {
        this.tokenizerFactory = tokenizerFactory;
    }

    public TokenizerFactory get(IndexSettings indexSettings, Environment environment, String name, Settings settings) throws IOException {
        Version indexVersion = Version.indexCreated(settings);
        if (!Version.CURRENT.equals(indexVersion)) {
            PreBuiltTokenizers preBuiltTokenizers = PreBuiltTokenizers.getOrDefault(name, null);
            if (preBuiltTokenizers != null) {
                return preBuiltTokenizers.getTokenizerFactory(indexVersion);
            }
        }

        return tokenizerFactory;
    }
}
