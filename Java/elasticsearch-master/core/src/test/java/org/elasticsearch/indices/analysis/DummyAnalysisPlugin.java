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

package org.elasticsearch.indices.analysis;

import org.elasticsearch.plugins.Plugin;

public class DummyAnalysisPlugin extends Plugin {
    /**
     * The name of the plugin.
     */
    @Override
    public String name() {
        return "analysis-dummy";
    }

    /**
     * The description of the plugin.
     */
    @Override
    public String description() {
        return "Analysis Dummy Plugin";
    }


    public void onModule(AnalysisModule module) {
        module.registerAnalyzer("dummy", (a, b, c, d) -> new DummyAnalyzerProvider());
        module.registerTokenFilter("dummy_token_filter", (a, b, c, d) -> new DummyTokenFilterFactory());
        module.registerTokenizer("dummy_tokenizer", (a, b, c, d) -> new DummyTokenizerFactory());
        module.registerCharFilter("dummy_char_filter", (a, b, c, d) -> new DummyCharFilterFactory());
    }

}
