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
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.test.ESTokenStreamTestCase;
import org.elasticsearch.test.IndexSettingsModule;

import static org.elasticsearch.common.settings.Settings.settingsBuilder;

public class StopAnalyzerTests extends ESTokenStreamTestCase {
    public void testDefaultsCompoundAnalysis() throws Exception {
        String json = "/org/elasticsearch/index/analysis/stop.json";
        Settings settings = settingsBuilder()
            .loadFromStream(json, getClass().getResourceAsStream(json))
                .put("path.home", createTempDir().toString())
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .build();
        IndexSettings idxSettings = IndexSettingsModule.newIndexSettings(new Index("index"), settings);
        AnalysisService analysisService = new AnalysisRegistry(null, new Environment(settings)).build(idxSettings);

        NamedAnalyzer analyzer1 = analysisService.analyzer("analyzer1");

        assertTokenStreamContents(analyzer1.tokenStream("test", "to be or not to be"), new String[0]);

        NamedAnalyzer analyzer2 = analysisService.analyzer("analyzer2");

        assertTokenStreamContents(analyzer2.tokenStream("test", "to be or not to be"), new String[0]);
    }
}
