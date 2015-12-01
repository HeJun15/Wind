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
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugin.analysis.AnalysisPhoneticPlugin;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.IndexSettingsModule;
import org.hamcrest.MatcherAssert;

import java.io.IOException;

import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.hamcrest.Matchers.instanceOf;

/**
 */
public class SimplePhoneticAnalysisTests extends ESTestCase {
    public void testPhoneticTokenFilterFactory() throws IOException {
        String yaml = "/org/elasticsearch/index/analysis/phonetic-1.yml";
        Settings settings = settingsBuilder().loadFromStream(yaml, getClass().getResourceAsStream(yaml))
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .put("path.home", createTempDir())
                .build();
        AnalysisService analysisService = testSimpleConfiguration(settings);
        TokenFilterFactory filterFactory = analysisService.tokenFilter("phonetic");
        MatcherAssert.assertThat(filterFactory, instanceOf(PhoneticTokenFilterFactory.class));
    }

    private AnalysisService testSimpleConfiguration(Settings settings) throws IOException {
        Index index = new Index("test");
        AnalysisModule analysisModule = new AnalysisModule(new Environment(settings));
        new AnalysisPhoneticPlugin().onModule(analysisModule);
        Injector parentInjector = new ModulesBuilder().add(new SettingsModule(settings, new SettingsFilter(settings)),
                new EnvironmentModule(new Environment(settings)), analysisModule)
                .createInjector();
        return parentInjector.getInstance(AnalysisRegistry.class).build(IndexSettingsModule.newIndexSettings(index, settings));
    }
}
