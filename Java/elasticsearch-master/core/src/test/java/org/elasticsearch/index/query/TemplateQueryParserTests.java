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
package org.elasticsearch.index.query;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Accountable;
import org.elasticsearch.Version;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.inject.multibindings.Multibinder;
import org.elasticsearch.common.inject.util.Providers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.cache.bitset.BitsetFilterCache;
import org.elasticsearch.index.fielddata.IndexFieldDataService;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.functionscore.ScoreFunctionParser;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.similarity.SimilarityService;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.indices.IndicesWarmer;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.indices.fielddata.cache.IndicesFieldDataCache;
import org.elasticsearch.indices.mapper.MapperRegistry;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.IndexSettingsModule;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.threadpool.ThreadPoolModule;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;

/**
 * Test parsing and executing a template request.
 */
// NOTE: this can't be migrated to ESSingleNodeTestCase because of the custom path.conf
public class TemplateQueryParserTests extends ESTestCase {

    private Injector injector;
    private QueryShardContext context;

    @Before
    public void setup() throws IOException {
        Settings settings = Settings.settingsBuilder()
                .put("path.home", createTempDir().toString())
                .put("path.conf", this.getDataPath("config"))
                .put("name", getClass().getName())
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .build();
        final Client proxy = (Client) Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class[]{Client.class}, (proxy1, method, args) -> {
                    throw new UnsupportedOperationException("client is just a dummy");
                });
        Index index = new Index("test");
        IndexSettings idxSettings = IndexSettingsModule.newIndexSettings(index, settings);
        injector = new ModulesBuilder().add(
                new EnvironmentModule(new Environment(settings)),
                new SettingsModule(settings, new SettingsFilter(settings)),
                new ThreadPoolModule(new ThreadPool(settings)),
                new IndicesModule() {
                    @Override
                    public void configure() {
                        // skip services
                        bindQueryParsersExtension();
                    }
                },
                new ScriptModule(settings),
                new IndexSettingsModule(index, settings),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(Client.class).toInstance(proxy); // not needed here
                        Multibinder.newSetBinder(binder(), ScoreFunctionParser.class);
                        bind(ClusterService.class).toProvider(Providers.of((ClusterService) null));
                        bind(CircuitBreakerService.class).to(NoneCircuitBreakerService.class);
                    }
                }
        ).createInjector();

        AnalysisService analysisService = new AnalysisRegistry(null, new Environment(settings)).build(idxSettings);
        ScriptService scriptService = injector.getInstance(ScriptService.class);
        SimilarityService similarityService = new SimilarityService(idxSettings, Collections.EMPTY_MAP);
        MapperRegistry mapperRegistry = new IndicesModule().getMapperRegistry();
        MapperService mapperService = new MapperService(idxSettings, analysisService, similarityService, mapperRegistry);
        IndexFieldDataService indexFieldDataService =new IndexFieldDataService(idxSettings, injector.getInstance(IndicesFieldDataCache.class), injector.getInstance(CircuitBreakerService.class), mapperService);
        BitsetFilterCache bitsetFilterCache = new BitsetFilterCache(idxSettings, new IndicesWarmer(idxSettings.getNodeSettings(), null), new BitsetFilterCache.Listener() {
            @Override
            public void onCache(ShardId shardId, Accountable accountable) {

            }

            @Override
            public void onRemoval(ShardId shardId, Accountable accountable) {

            }
        });
        IndicesQueriesRegistry indicesQueriesRegistry = injector.getInstance(IndicesQueriesRegistry.class);
        context = new QueryShardContext(idxSettings, proxy, bitsetFilterCache, indexFieldDataService, mapperService, similarityService, scriptService, indicesQueriesRegistry);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        terminate(injector.getInstance(ThreadPool.class));
    }

    public void testParser() throws IOException {
        String templateString = "{" + "\"query\":{\"match_{{template}}\": {}}," + "\"params\":{\"template\":\"all\"}" + "}";

        XContentParser templateSourceParser = XContentFactory.xContent(templateString).createParser(templateString);
        context.reset(templateSourceParser);
        templateSourceParser.nextToken();

        TemplateQueryParser parser = injector.getInstance(TemplateQueryParser.class);
        Query query = parser.fromXContent(context.parseContext()).toQuery(context);
        assertTrue("Parsing template query failed.", query instanceof MatchAllDocsQuery);
    }

    public void testParseTemplateAsSingleStringWithConditionalClause() throws IOException {
        String templateString = "{" + "  \"inline\" : \"{ \\\"match_{{#use_it}}{{template}}{{/use_it}}\\\":{} }\"," + "  \"params\":{"
                + "    \"template\":\"all\"," + "    \"use_it\": true" + "  }" + "}";
        XContentParser templateSourceParser = XContentFactory.xContent(templateString).createParser(templateString);
        context.reset(templateSourceParser);

        TemplateQueryParser parser = injector.getInstance(TemplateQueryParser.class);
        Query query = parser.fromXContent(context.parseContext()).toQuery(context);
        assertTrue("Parsing template query failed.", query instanceof MatchAllDocsQuery);
    }

    /**
     * Test that the template query parser can parse and evaluate template
     * expressed as a single string but still it expects only the query
     * specification (thus this test should fail with specific exception).
     */
    public void testParseTemplateFailsToParseCompleteQueryAsSingleString() throws IOException {
        String templateString = "{" + "  \"inline\" : \"{ \\\"size\\\": \\\"{{size}}\\\", \\\"query\\\":{\\\"match_all\\\":{}}}\","
                + "  \"params\":{" + "    \"size\":2" + "  }\n" + "}";

        XContentParser templateSourceParser = XContentFactory.xContent(templateString).createParser(templateString);
        context.reset(templateSourceParser);

        TemplateQueryParser parser = injector.getInstance(TemplateQueryParser.class);
        try {
            parser.fromXContent(context.parseContext()).toQuery(context);
            fail("Expected ParsingException");
        } catch (ParsingException e) {
            assertThat(e.getMessage(), containsString("query malformed, no field after start_object"));
        }
    }

    public void testParserCanExtractTemplateNames() throws Exception {
        String templateString = "{ \"file\": \"storedTemplate\" ,\"params\":{\"template\":\"all\" } } ";

        XContentParser templateSourceParser = XContentFactory.xContent(templateString).createParser(templateString);
        context.reset(templateSourceParser);
        templateSourceParser.nextToken();

        TemplateQueryParser parser = injector.getInstance(TemplateQueryParser.class);
        Query query = parser.fromXContent(context.parseContext()).toQuery(context);
        assertTrue("Parsing template query failed.", query instanceof MatchAllDocsQuery);
    }
}
