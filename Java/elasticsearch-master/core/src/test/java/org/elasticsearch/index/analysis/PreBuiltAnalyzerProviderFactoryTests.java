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
import org.elasticsearch.indices.analysis.PreBuiltAnalyzers;
import org.elasticsearch.test.ESTestCase;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 *
 */
public class PreBuiltAnalyzerProviderFactoryTests extends ESTestCase {
    public void testVersioningInFactoryProvider() throws Exception {
        PreBuiltAnalyzerProviderFactory factory = new PreBuiltAnalyzerProviderFactory("default", AnalyzerScope.INDEX, PreBuiltAnalyzers.STANDARD.getAnalyzer(Version.CURRENT));

        AnalyzerProvider former090AnalyzerProvider = factory.create("default", Settings.settingsBuilder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_0_90_0).build());
        AnalyzerProvider currentAnalyzerProviderReference = factory.create("default", Settings.settingsBuilder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT).build());

        // would love to access the version inside of the lucene analyzer, but that is not possible...
        assertThat(currentAnalyzerProviderReference, is(not(former090AnalyzerProvider)));
    }
}
