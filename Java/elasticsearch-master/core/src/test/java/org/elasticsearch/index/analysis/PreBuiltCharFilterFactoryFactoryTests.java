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
import org.elasticsearch.indices.analysis.PreBuiltCharFilters;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;

/**
 *
 */
public class PreBuiltCharFilterFactoryFactoryTests extends ESTestCase {
    public void testThatDifferentVersionsCanBeLoaded() throws IOException {
        PreBuiltCharFilterFactoryFactory factory = new PreBuiltCharFilterFactoryFactory(PreBuiltCharFilters.HTML_STRIP.getCharFilterFactory(Version.CURRENT));

        CharFilterFactory former090TokenizerFactory = factory.get(null, null, "html_strip", Settings.settingsBuilder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_0_90_0).build());
        CharFilterFactory former090TokenizerFactoryCopy = factory.get(null, null, "html_strip", Settings.settingsBuilder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_0_90_0).build());
        CharFilterFactory currentTokenizerFactory = factory.get(null, null, "html_strip", Settings.settingsBuilder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT).build());

        assertThat(currentTokenizerFactory, is(former090TokenizerFactory));
        assertThat(currentTokenizerFactory, is(former090TokenizerFactoryCopy));
    }
}
