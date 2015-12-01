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
package org.elasticsearch.search.functionscore;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.test.ESBackcompatTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.elasticsearch.client.Requests.searchRequest;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.functionScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders.gaussDecayFunction;
import static org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders.scriptFunction;
import static org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders.weightFactorFunction;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertOrderedSearchHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;

/**
 */
public class FunctionScoreBackwardCompatibilityIT extends ESBackcompatTestCase {
    /**
     * Simple upgrade test for function score.
     */
    public void testSimpleFunctionScoreParsingWorks() throws IOException, ExecutionException, InterruptedException {
        assertAcked(prepareCreate("test").addMapping(
                "type1",
                jsonBuilder().startObject()
                        .startObject("type1")
                        .startObject("properties")
                        .startObject("text")
                        .field("type", "string")
                        .endObject()
                        .startObject("loc")
                        .field("type", "geo_point")
                        .endObject()
                        .endObject()
                        .endObject()
                        .endObject()));
        ensureYellow();

        int numDocs = 10;
        String[] ids = new String[numDocs];
        List<IndexRequestBuilder> indexBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            String id = Integer.toString(i);
            indexBuilders.add(client().prepareIndex()
                    .setType("type1").setId(id).setIndex("test")
                    .setSource(
                            jsonBuilder().startObject()
                                    .field("text", "value " + (i < 5 ? "boosted" : ""))
                                    .startObject("loc")
                                    .field("lat", 10 + i)
                                    .field("lon", 20)
                                    .endObject()
                                    .endObject()));
            ids[i] = id;
        }
        indexRandom(true, indexBuilders);
        checkFunctionScoreStillWorks(ids);
        logClusterState();
        // prevent any kind of allocation during the upgrade we recover from gateway
        disableAllocation("test");
        boolean upgraded;
        int upgradedNodesCounter = 1;
        do {
            logger.debug("function_score bwc: upgrading {}st node", upgradedNodesCounter++);
            upgraded = backwardsCluster().upgradeOneNode();
            ensureYellow();
            logClusterState();
            checkFunctionScoreStillWorks(ids);
        } while (upgraded);
        enableAllocation("test");
        logger.debug("done function_score while upgrading");
    }

    @Override
    protected Settings commonNodeSettings(int nodeOrdinal) {
        return Settings.builder().put(super.commonNodeSettings(nodeOrdinal))
                .put("script.inline", "on").build();
    }

    private void checkFunctionScoreStillWorks(String... ids) throws ExecutionException, InterruptedException, IOException {
        SearchResponse response = client().search(
                searchRequest().source(
                        searchSource().query(
                                functionScoreQuery(termQuery("text", "value"), new FunctionScoreQueryBuilder.FilterFunctionBuilder[] {
                                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(gaussDecayFunction("loc", new GeoPoint(10, 20), "1000km")),
                                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(scriptFunction(new Script("_index['text']['value'].tf()"))),
                                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(termQuery("text", "boosted"), weightFactorFunction(5))
                                        }
                                )))).actionGet();
        assertSearchResponse(response);
        assertOrderedSearchHits(response, ids);
    }
}
