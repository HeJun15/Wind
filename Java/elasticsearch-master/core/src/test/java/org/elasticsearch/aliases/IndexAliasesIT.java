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

package org.elasticsearch.aliases;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.exists.AliasesExistResponse;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.AliasAction;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.AliasOrIndex;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.action.admin.indices.alias.delete.AliasesNotFoundException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.global.Global;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.ESIntegTestCase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.client.Requests.createIndexRequest;
import static org.elasticsearch.client.Requests.indexRequest;
import static org.elasticsearch.cluster.metadata.IndexMetaData.INDEX_METADATA_BLOCK;
import static org.elasticsearch.cluster.metadata.IndexMetaData.INDEX_READ_ONLY_BLOCK;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_BLOCKS_METADATA;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_BLOCKS_READ;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_BLOCKS_WRITE;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_READ_ONLY;
import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.elasticsearch.index.query.QueryBuilders.hasChildQuery;
import static org.elasticsearch.index.query.QueryBuilders.hasParentQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.test.hamcrest.CollectionAssertions.hasKey;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertBlocked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class IndexAliasesIT extends ESIntegTestCase {
    public void testAliases() throws Exception {
        logger.info("--> creating index [test]");
        createIndex("test");

        ensureGreen();

        logger.info("--> aliasing index [test] with [alias1]");
        assertAcked(admin().indices().prepareAliases().addAlias("test", "alias1"));

        logger.info("--> indexing against [alias1], should work now");
        IndexResponse indexResponse = client().index(indexRequest("alias1").type("type1").id("1").source(source("1", "test"))).actionGet();
        assertThat(indexResponse.getIndex(), equalTo("test"));

        logger.info("--> creating index [test_x]");
        createIndex("test_x");

        ensureGreen();

        logger.info("--> remove [alias1], Aliasing index [test_x] with [alias1]");
        assertAcked(admin().indices().prepareAliases().removeAlias("test", "alias1").addAlias("test_x", "alias1"));

        logger.info("--> indexing against [alias1], should work against [test_x]");
        indexResponse = client().index(indexRequest("alias1").type("type1").id("1").source(source("1", "test"))).actionGet();
        assertThat(indexResponse.getIndex(), equalTo("test_x"));
    }

    public void testFailedFilter() throws Exception {
        logger.info("--> creating index [test]");
        createIndex("test");

        ensureGreen();

        //invalid filter, invalid json
        IndicesAliasesRequestBuilder indicesAliasesRequestBuilder = admin().indices().prepareAliases().addAlias("test", "alias1", "abcde");
        try {
            indicesAliasesRequestBuilder.get();
            fail("put alias should have been failed due to invalid filter");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("failed to parse filter for alias [alias1]"));
        }

        //valid json , invalid filter
        indicesAliasesRequestBuilder = admin().indices().prepareAliases().addAlias("test", "alias1", "{ \"test\": {} }");
        try {
            indicesAliasesRequestBuilder.get();
            fail("put alias should have been failed due to invalid filter");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("failed to parse filter for alias [alias1]"));
        }
    }

    public void testFilteringAliases() throws Exception {
        logger.info("--> creating index [test]");
        assertAcked(prepareCreate("test").addMapping("type", "user", "type=string"));

        ensureGreen();

        logger.info("--> aliasing index [test] with [alias1] and filter [user:kimchy]");
        QueryBuilder filter = termQuery("user", "kimchy");
        assertAcked(admin().indices().prepareAliases().addAlias("test", "alias1", filter));

        // For now just making sure that filter was stored with the alias
        logger.info("--> making sure that filter was stored with alias [alias1] and filter [user:kimchy]");
        ClusterState clusterState = admin().cluster().prepareState().get().getState();
        IndexMetaData indexMd = clusterState.metaData().index("test");
        assertThat(indexMd.getAliases().get("alias1").filter().string(), equalTo("{\"term\":{\"user\":{\"value\":\"kimchy\",\"boost\":1.0}}}"));

    }

    public void testEmptyFilter() throws Exception {
        logger.info("--> creating index [test]");
        createIndex("test");
        ensureGreen();

        logger.info("--> aliasing index [test] with [alias1] and empty filter");
        assertAcked(admin().indices().prepareAliases().addAlias("test", "alias1", "{}"));
    }

    public void testSearchingFilteringAliasesSingleIndex() throws Exception {
        logger.info("--> creating index [test]");
        assertAcked(prepareCreate("test").addMapping("type1", "id", "type=string", "name", "type=string"));

        ensureGreen();

        logger.info("--> adding filtering aliases to index [test]");
        assertAcked(admin().indices().prepareAliases().addAlias("test", "alias1"));
        assertAcked(admin().indices().prepareAliases().addAlias("test", "alias2"));
        assertAcked(admin().indices().prepareAliases().addAlias("test", "foos", termQuery("name", "foo")));
        assertAcked(admin().indices().prepareAliases().addAlias("test", "bars", termQuery("name", "bar")));
        assertAcked(admin().indices().prepareAliases().addAlias("test", "tests", termQuery("name", "test")));

        logger.info("--> indexing against [test]");
        client().index(indexRequest("test").type("type1").id("1").source(source("1", "foo test")).refresh(true)).actionGet();
        client().index(indexRequest("test").type("type1").id("2").source(source("2", "bar test")).refresh(true)).actionGet();
        client().index(indexRequest("test").type("type1").id("3").source(source("3", "baz test")).refresh(true)).actionGet();
        client().index(indexRequest("test").type("type1").id("4").source(source("4", "something else")).refresh(true)).actionGet();

        logger.info("--> checking single filtering alias search");
        SearchResponse searchResponse = client().prepareSearch("foos").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1");

        logger.info("--> checking single filtering alias wildcard search");
        searchResponse = client().prepareSearch("fo*").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1");

        searchResponse = client().prepareSearch("tests").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1", "2", "3");

        logger.info("--> checking single filtering alias search with sort");
        searchResponse = client().prepareSearch("tests").setQuery(QueryBuilders.matchAllQuery()).addSort("_uid", SortOrder.ASC).get();
        assertHits(searchResponse.getHits(), "1", "2", "3");

        logger.info("--> checking single filtering alias search with global facets");
        searchResponse = client().prepareSearch("tests").setQuery(QueryBuilders.matchQuery("name", "bar"))
                .addAggregation(AggregationBuilders.global("global").subAggregation(AggregationBuilders.terms("test").field("name")))
                .get();
        assertSearchResponse(searchResponse);
        Global global = searchResponse.getAggregations().get("global");
        Terms terms = global.getAggregations().get("test");
        assertThat(terms.getBuckets().size(), equalTo(4));

        logger.info("--> checking single filtering alias search with global facets and sort");
        searchResponse = client().prepareSearch("tests").setQuery(QueryBuilders.matchQuery("name", "bar"))
                .addAggregation(AggregationBuilders.global("global").subAggregation(AggregationBuilders.terms("test").field("name")))
                .addSort("_uid", SortOrder.ASC).get();
        assertSearchResponse(searchResponse);
        global = searchResponse.getAggregations().get("global");
        terms = global.getAggregations().get("test");
        assertThat(terms.getBuckets().size(), equalTo(4));

        logger.info("--> checking single filtering alias search with non-global facets");
        searchResponse = client().prepareSearch("tests").setQuery(QueryBuilders.matchQuery("name", "bar"))
                .addAggregation(AggregationBuilders.terms("test").field("name"))
                .addSort("_uid", SortOrder.ASC).get();
        assertSearchResponse(searchResponse);
        terms = searchResponse.getAggregations().get("test");
        assertThat(terms.getBuckets().size(), equalTo(2));

        searchResponse = client().prepareSearch("foos", "bars").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1", "2");

        logger.info("--> checking single non-filtering alias search");
        searchResponse = client().prepareSearch("alias1").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1", "2", "3", "4");

        logger.info("--> checking non-filtering alias and filtering alias search");
        searchResponse = client().prepareSearch("alias1", "foos").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1", "2", "3", "4");

        logger.info("--> checking index and filtering alias search");
        searchResponse = client().prepareSearch("test", "foos").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1", "2", "3", "4");

        logger.info("--> checking index and alias wildcard search");
        searchResponse = client().prepareSearch("te*", "fo*").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1", "2", "3", "4");
    }

    public void testSearchingFilteringAliasesTwoIndices() throws Exception {
        logger.info("--> creating index [test1]");
        assertAcked(prepareCreate("test1").addMapping("type1", "name", "type=string"));
        logger.info("--> creating index [test2]");
        assertAcked(prepareCreate("test2").addMapping("type1", "name", "type=string"));
        ensureGreen();

        logger.info("--> adding filtering aliases to index [test1]");
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "aliasToTest1"));
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "aliasToTests"));
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "foos", termQuery("name", "foo")));
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "bars", termQuery("name", "bar")));

        logger.info("--> adding filtering aliases to index [test2]");
        assertAcked(admin().indices().prepareAliases().addAlias("test2", "aliasToTest2"));
        assertAcked(admin().indices().prepareAliases().addAlias("test2", "aliasToTests"));
        assertAcked(admin().indices().prepareAliases().addAlias("test2", "foos", termQuery("name", "foo")));

        logger.info("--> indexing against [test1]");
        client().index(indexRequest("test1").type("type1").id("1").source(source("1", "foo test"))).get();
        client().index(indexRequest("test1").type("type1").id("2").source(source("2", "bar test"))).get();
        client().index(indexRequest("test1").type("type1").id("3").source(source("3", "baz test"))).get();
        client().index(indexRequest("test1").type("type1").id("4").source(source("4", "something else"))).get();

        logger.info("--> indexing against [test2]");
        client().index(indexRequest("test2").type("type1").id("5").source(source("5", "foo test"))).get();
        client().index(indexRequest("test2").type("type1").id("6").source(source("6", "bar test"))).get();
        client().index(indexRequest("test2").type("type1").id("7").source(source("7", "baz test"))).get();
        client().index(indexRequest("test2").type("type1").id("8").source(source("8", "something else"))).get();

        refresh();

        logger.info("--> checking filtering alias for two indices");
        SearchResponse searchResponse = client().prepareSearch("foos").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1", "5");
        assertThat(client().prepareSearch("foos").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(2L));

        logger.info("--> checking filtering alias for one index");
        searchResponse = client().prepareSearch("bars").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "2");
        assertThat(client().prepareSearch("bars").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(1L));

        logger.info("--> checking filtering alias for two indices and one complete index");
        searchResponse = client().prepareSearch("foos", "test1").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1", "2", "3", "4", "5");
        assertThat(client().prepareSearch("foos", "test1").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(5L));

        logger.info("--> checking filtering alias for two indices and non-filtering alias for one index");
        searchResponse = client().prepareSearch("foos", "aliasToTest1").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "1", "2", "3", "4", "5");
        assertThat(client().prepareSearch("foos", "aliasToTest1").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(5L));

        logger.info("--> checking filtering alias for two indices and non-filtering alias for both indices");
        searchResponse = client().prepareSearch("foos", "aliasToTests").setQuery(QueryBuilders.matchAllQuery()).get();
        assertThat(searchResponse.getHits().totalHits(), equalTo(8L));
        assertThat(client().prepareSearch("foos", "aliasToTests").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(8L));

        logger.info("--> checking filtering alias for two indices and non-filtering alias for both indices");
        searchResponse = client().prepareSearch("foos", "aliasToTests").setQuery(QueryBuilders.termQuery("name", "something")).get();
        assertHits(searchResponse.getHits(), "4", "8");
        assertThat(client().prepareSearch("foos", "aliasToTests").setSize(0).setQuery(QueryBuilders.termQuery("name", "something")).get().getHits().totalHits(), equalTo(2L));
    }

    public void testSearchingFilteringAliasesMultipleIndices() throws Exception {
        logger.info("--> creating indices");
        createIndex("test1", "test2", "test3");

        assertAcked(client().admin().indices().preparePutMapping("test1", "test2", "test3")
                .setType("type1")
                .setSource("name", "type=string"));

        ensureGreen();

        logger.info("--> adding aliases to indices");
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "alias12"));
        assertAcked(admin().indices().prepareAliases().addAlias("test2", "alias12"));

        logger.info("--> adding filtering aliases to indices");
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "filter1", termQuery("name", "test1")));

        assertAcked(admin().indices().prepareAliases().addAlias("test2", "filter23", termQuery("name", "foo")));
        assertAcked(admin().indices().prepareAliases().addAlias("test3", "filter23", termQuery("name", "foo")));

        assertAcked(admin().indices().prepareAliases().addAlias("test1", "filter13", termQuery("name", "baz")));
        assertAcked(admin().indices().prepareAliases().addAlias("test3", "filter13", termQuery("name", "baz")));

        logger.info("--> indexing against [test1]");
        client().index(indexRequest("test1").type("type1").id("11").source(source("11", "foo test1"))).get();
        client().index(indexRequest("test1").type("type1").id("12").source(source("12", "bar test1"))).get();
        client().index(indexRequest("test1").type("type1").id("13").source(source("13", "baz test1"))).get();

        client().index(indexRequest("test2").type("type1").id("21").source(source("21", "foo test2"))).get();
        client().index(indexRequest("test2").type("type1").id("22").source(source("22", "bar test2"))).get();
        client().index(indexRequest("test2").type("type1").id("23").source(source("23", "baz test2"))).get();

        client().index(indexRequest("test3").type("type1").id("31").source(source("31", "foo test3"))).get();
        client().index(indexRequest("test3").type("type1").id("32").source(source("32", "bar test3"))).get();
        client().index(indexRequest("test3").type("type1").id("33").source(source("33", "baz test3"))).get();

        refresh();

        logger.info("--> checking filtering alias for multiple indices");
        SearchResponse searchResponse = client().prepareSearch("filter23", "filter13").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "21", "31", "13", "33");
        assertThat(client().prepareSearch("filter23", "filter13").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(4L));

        searchResponse = client().prepareSearch("filter23", "filter1").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "21", "31", "11", "12", "13");
        assertThat(client().prepareSearch("filter23", "filter1").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(5L));

        searchResponse = client().prepareSearch("filter13", "filter1").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "11", "12", "13", "33");
        assertThat(client().prepareSearch("filter13", "filter1").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(4L));

        searchResponse = client().prepareSearch("filter13", "filter1", "filter23").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "11", "12", "13", "21", "31", "33");
        assertThat(client().prepareSearch("filter13", "filter1", "filter23").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(6L));

        searchResponse = client().prepareSearch("filter23", "filter13", "test2").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "21", "22", "23", "31", "13", "33");
        assertThat(client().prepareSearch("filter23", "filter13", "test2").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(6L));

        searchResponse = client().prepareSearch("filter23", "filter13", "test1", "test2").setQuery(QueryBuilders.matchAllQuery()).get();
        assertHits(searchResponse.getHits(), "11", "12", "13", "21", "22", "23", "31", "33");
        assertThat(client().prepareSearch("filter23", "filter13", "test1", "test2").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(8L));
    }

    public void testDeletingByQueryFilteringAliases() throws Exception {
        logger.info("--> creating index [test1] and [test2");
        assertAcked(prepareCreate("test1").addMapping("type1", "name", "type=string"));
        assertAcked(prepareCreate("test2").addMapping("type1", "name", "type=string"));
        ensureGreen();

        logger.info("--> adding filtering aliases to index [test1]");
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "aliasToTest1"));
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "aliasToTests"));
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "foos", termQuery("name", "foo")));
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "bars", termQuery("name", "bar")));
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "tests", termQuery("name", "test")));

        logger.info("--> adding filtering aliases to index [test2]");
        assertAcked(admin().indices().prepareAliases().addAlias("test2", "aliasToTest2"));
        assertAcked(admin().indices().prepareAliases().addAlias("test2", "aliasToTests"));
        assertAcked(admin().indices().prepareAliases().addAlias("test2", "foos", termQuery("name", "foo")));
        assertAcked(admin().indices().prepareAliases().addAlias("test2", "tests", termQuery("name", "test")));

        logger.info("--> indexing against [test1]");
        client().index(indexRequest("test1").type("type1").id("1").source(source("1", "foo test"))).get();
        client().index(indexRequest("test1").type("type1").id("2").source(source("2", "bar test"))).get();
        client().index(indexRequest("test1").type("type1").id("3").source(source("3", "baz test"))).get();
        client().index(indexRequest("test1").type("type1").id("4").source(source("4", "something else"))).get();

        logger.info("--> indexing against [test2]");
        client().index(indexRequest("test2").type("type1").id("5").source(source("5", "foo test"))).get();
        client().index(indexRequest("test2").type("type1").id("6").source(source("6", "bar test"))).get();
        client().index(indexRequest("test2").type("type1").id("7").source(source("7", "baz test"))).get();
        client().index(indexRequest("test2").type("type1").id("8").source(source("8", "something else"))).get();

        refresh();

        logger.info("--> checking counts before delete");
        assertThat(client().prepareSearch("bars").setSize(0).setQuery(QueryBuilders.matchAllQuery()).get().getHits().totalHits(), equalTo(1L));
    }

    public void testDeleteAliases() throws Exception {
        logger.info("--> creating index [test1] and [test2]");
        assertAcked(prepareCreate("test1").addMapping("type", "name", "type=string"));
        assertAcked(prepareCreate("test2").addMapping("type", "name", "type=string"));
        ensureGreen();

        logger.info("--> adding filtering aliases to index [test1]");
        assertAcked(admin().indices().prepareAliases().addAlias("test1", "aliasToTest1")
                .addAlias("test1", "aliasToTests")
                .addAlias("test1", "foos", termQuery("name", "foo"))
                .addAlias("test1", "bars", termQuery("name", "bar"))
                .addAlias("test1", "tests", termQuery("name", "test")));

        logger.info("--> adding filtering aliases to index [test2]");
        assertAcked(admin().indices().prepareAliases().addAlias("test2", "aliasToTest2")
                .addAlias("test2", "aliasToTests")
                .addAlias("test2", "foos", termQuery("name", "foo"))
                .addAlias("test2", "tests", termQuery("name", "test")));

        String[] indices = {"test1", "test2"};
        String[] aliases = {"aliasToTest1", "foos", "bars", "tests", "aliasToTest2", "aliasToTests"};

        admin().indices().prepareAliases().removeAlias(indices, aliases).get();

        AliasesExistResponse response = admin().indices().prepareAliasesExist(aliases).get();
        assertThat(response.exists(), equalTo(false));
    }

    public void testWaitForAliasCreationMultipleShards() throws Exception {
        logger.info("--> creating index [test]");
        createIndex("test");

        ensureGreen();

        for (int i = 0; i < 10; i++) {
            assertAcked(admin().indices().prepareAliases().addAlias("test", "alias" + i));
            client().index(indexRequest("alias" + i).type("type1").id("1").source(source("1", "test"))).get();
        }
    }

    public void testWaitForAliasCreationSingleShard() throws Exception {
        logger.info("--> creating index [test]");
        assertAcked(admin().indices().create(createIndexRequest("test").settings(settingsBuilder().put("index.numberOfReplicas", 0).put("index.numberOfShards", 1))).get());

        ensureGreen();

        for (int i = 0; i < 10; i++) {
            assertAcked(admin().indices().prepareAliases().addAlias("test", "alias" + i));
            client().index(indexRequest("alias" + i).type("type1").id("1").source(source("1", "test"))).get();
        }
    }

    public void testWaitForAliasSimultaneousUpdate() throws Exception {
        final int aliasCount = 10;

        logger.info("--> creating index [test]");
        createIndex("test");

        ensureGreen();

        ExecutorService executor = Executors.newFixedThreadPool(aliasCount);
        for (int i = 0; i < aliasCount; i++) {
            final String aliasName = "alias" + i;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    assertAcked(admin().indices().prepareAliases().addAlias("test", aliasName));
                    client().index(indexRequest(aliasName).type("type1").id("1").source(source("1", "test"))).actionGet();
                }
            });
        }
        executor.shutdown();
        boolean done = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(done, equalTo(true));
        if (!done) {
            executor.shutdownNow();
        }
    }

    public void testSameAlias() throws Exception {
        logger.info("--> creating index [test]");
        assertAcked(prepareCreate("test").addMapping("type", "name", "type=string"));
        ensureGreen();

        logger.info("--> creating alias1 ");
        assertAcked((admin().indices().prepareAliases().addAlias("test", "alias1")));
        TimeValue timeout = TimeValue.timeValueSeconds(2);
        logger.info("--> recreating alias1 ");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        assertAcked((admin().indices().prepareAliases().addAlias("test", "alias1").setTimeout(timeout)));
        assertThat(stopWatch.stop().lastTaskTime().millis(), lessThan(timeout.millis()));

        logger.info("--> modifying alias1 to have a filter");
        stopWatch.start();
        assertAcked((admin().indices().prepareAliases().addAlias("test", "alias1", termQuery("name", "foo")).setTimeout(timeout)));
        assertThat(stopWatch.stop().lastTaskTime().millis(), lessThan(timeout.millis()));

        logger.info("--> recreating alias1 with the same filter");
        stopWatch.start();
        assertAcked((admin().indices().prepareAliases().addAlias("test", "alias1", termQuery("name", "foo")).setTimeout(timeout)));
        assertThat(stopWatch.stop().lastTaskTime().millis(), lessThan(timeout.millis()));

        logger.info("--> recreating alias1 with a different filter");
        stopWatch.start();
        assertAcked((admin().indices().prepareAliases().addAlias("test", "alias1", termQuery("name", "bar")).setTimeout(timeout)));
        assertThat(stopWatch.stop().lastTaskTime().millis(), lessThan(timeout.millis()));

        logger.info("--> verify that filter was updated");
        AliasMetaData aliasMetaData = ((AliasOrIndex.Alias) internalCluster().clusterService().state().metaData().getAliasAndIndexLookup().get("alias1")).getFirstAliasMetaData();
        assertThat(aliasMetaData.getFilter().toString(), equalTo("{\"term\":{\"name\":{\"value\":\"bar\",\"boost\":1.0}}}"));

        logger.info("--> deleting alias1");
        stopWatch.start();
        assertAcked((admin().indices().prepareAliases().removeAlias("test", "alias1").setTimeout(timeout)));
        assertThat(stopWatch.stop().lastTaskTime().millis(), lessThan(timeout.millis()));


    }

    public void testIndicesRemoveNonExistingAliasResponds404() throws Exception {
        logger.info("--> creating index [test]");
        createIndex("test");
        ensureGreen();
        logger.info("--> deleting alias1 which does not exist");
        try {
            admin().indices().prepareAliases().removeAlias("test", "alias1").get();
            fail("Expected AliasesNotFoundException");
        } catch (AliasesNotFoundException e) {
            assertThat(e.getMessage(), containsString("[alias1] missing"));
        }
    }

    public void testIndicesGetAliases() throws Exception {
        logger.info("--> creating indices [foobar, test, test123, foobarbaz, bazbar]");
        createIndex("foobar");
        createIndex("test");
        createIndex("test123");
        createIndex("foobarbaz");
        createIndex("bazbar");

        assertAcked(client().admin().indices().preparePutMapping("foobar", "test", "test123", "foobarbaz", "bazbar")
                .setType("type").setSource("field", "type=string"));
        ensureGreen();

        logger.info("--> creating aliases [alias1, alias2]");
        assertAcked(admin().indices().prepareAliases().addAlias("foobar", "alias1").addAlias("foobar", "alias2"));

        logger.info("--> getting alias1");
        GetAliasesResponse getResponse = admin().indices().prepareGetAliases("alias1").get();
        assertThat(getResponse, notNullValue());
        assertThat(getResponse.getAliases().size(), equalTo(1));
        assertThat(getResponse.getAliases().get("foobar").size(), equalTo(1));
        assertThat(getResponse.getAliases().get("foobar").get(0), notNullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).alias(), equalTo("alias1"));
        assertThat(getResponse.getAliases().get("foobar").get(0).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getSearchRouting(), nullValue());
        AliasesExistResponse existsResponse = admin().indices().prepareAliasesExist("alias1").get();
        assertThat(existsResponse.exists(), equalTo(true));

        logger.info("--> getting all aliases that start with alias*");
        getResponse = admin().indices().prepareGetAliases("alias*").get();
        assertThat(getResponse, notNullValue());
        assertThat(getResponse.getAliases().size(), equalTo(1));
        assertThat(getResponse.getAliases().get("foobar").size(), equalTo(2));
        assertThat(getResponse.getAliases().get("foobar").get(0), notNullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).alias(), equalTo("alias1"));
        assertThat(getResponse.getAliases().get("foobar").get(0).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getSearchRouting(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(1), notNullValue());
        assertThat(getResponse.getAliases().get("foobar").get(1).alias(), equalTo("alias2"));
        assertThat(getResponse.getAliases().get("foobar").get(1).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(1).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(1).getSearchRouting(), nullValue());
        existsResponse = admin().indices().prepareAliasesExist("alias*").get();
        assertThat(existsResponse.exists(), equalTo(true));


        logger.info("--> creating aliases [bar, baz, foo]");
        assertAcked(admin().indices().prepareAliases()
                .addAlias("bazbar", "bar")
                .addAlias("bazbar", "bac", termQuery("field", "value"))
                .addAlias("foobar", "foo"));

        assertAcked(admin().indices().prepareAliases()
                .addAliasAction(new AliasAction(AliasAction.Type.ADD, "foobar", "bac").routing("bla")));

        logger.info("--> getting bar and baz for index bazbar");
        getResponse = admin().indices().prepareGetAliases("bar", "bac").addIndices("bazbar").get();
        assertThat(getResponse, notNullValue());
        assertThat(getResponse.getAliases().size(), equalTo(1));
        assertThat(getResponse.getAliases().get("bazbar").size(), equalTo(2));
        assertThat(getResponse.getAliases().get("bazbar").get(0), notNullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(0).alias(), equalTo("bac"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getFilter().string(), containsString("term"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getFilter().string(), containsString("field"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getFilter().string(), containsString("value"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(0).getSearchRouting(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1), notNullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1).alias(), equalTo("bar"));
        assertThat(getResponse.getAliases().get("bazbar").get(1).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1).getSearchRouting(), nullValue());
        existsResponse = admin().indices().prepareAliasesExist("bar", "bac")
                .addIndices("bazbar").get();
        assertThat(existsResponse.exists(), equalTo(true));

        logger.info("--> getting *b* for index baz*");
        getResponse = admin().indices().prepareGetAliases("*b*").addIndices("baz*").get();
        assertThat(getResponse, notNullValue());
        assertThat(getResponse.getAliases().size(), equalTo(1));
        assertThat(getResponse.getAliases().get("bazbar").size(), equalTo(2));
        assertThat(getResponse.getAliases().get("bazbar").get(0), notNullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(0).alias(), equalTo("bac"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getFilter().string(), containsString("term"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getFilter().string(), containsString("field"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getFilter().string(), containsString("value"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(0).getSearchRouting(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1), notNullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1).alias(), equalTo("bar"));
        assertThat(getResponse.getAliases().get("bazbar").get(1).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1).getSearchRouting(), nullValue());
        existsResponse = admin().indices().prepareAliasesExist("*b*")
                .addIndices("baz*").get();
        assertThat(existsResponse.exists(), equalTo(true));

        logger.info("--> getting *b* for index *bar");
        getResponse = admin().indices().prepareGetAliases("b*").addIndices("*bar").get();
        assertThat(getResponse, notNullValue());
        assertThat(getResponse.getAliases().size(), equalTo(2));
        assertThat(getResponse.getAliases().get("bazbar").size(), equalTo(2));
        assertThat(getResponse.getAliases().get("bazbar").get(0), notNullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(0).alias(), equalTo("bac"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getFilter().string(), containsString("term"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getFilter().string(), containsString("field"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getFilter().string(), containsString("value"));
        assertThat(getResponse.getAliases().get("bazbar").get(0).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(0).getSearchRouting(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1), notNullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1).alias(), equalTo("bar"));
        assertThat(getResponse.getAliases().get("bazbar").get(1).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("bazbar").get(1).getSearchRouting(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0), notNullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).alias(), equalTo("bac"));
        assertThat(getResponse.getAliases().get("foobar").get(0).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getIndexRouting(), equalTo("bla"));
        assertThat(getResponse.getAliases().get("foobar").get(0).getSearchRouting(), equalTo("bla"));
        existsResponse = admin().indices().prepareAliasesExist("b*")
                .addIndices("*bar").get();
        assertThat(existsResponse.exists(), equalTo(true));

        logger.info("--> getting f* for index *bar");
        getResponse = admin().indices().prepareGetAliases("f*").addIndices("*bar").get();
        assertThat(getResponse, notNullValue());
        assertThat(getResponse.getAliases().size(), equalTo(1));
        assertThat(getResponse.getAliases().get("foobar").get(0), notNullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).alias(), equalTo("foo"));
        assertThat(getResponse.getAliases().get("foobar").get(0).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getSearchRouting(), nullValue());
        existsResponse = admin().indices().prepareAliasesExist("f*")
                .addIndices("*bar").get();
        assertThat(existsResponse.exists(), equalTo(true));

        // alias at work
        logger.info("--> getting f* for index *bac");
        getResponse = admin().indices().prepareGetAliases("foo").addIndices("*bac").get();
        assertThat(getResponse, notNullValue());
        assertThat(getResponse.getAliases().size(), equalTo(1));
        assertThat(getResponse.getAliases().get("foobar").size(), equalTo(1));
        assertThat(getResponse.getAliases().get("foobar").get(0), notNullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).alias(), equalTo("foo"));
        assertThat(getResponse.getAliases().get("foobar").get(0).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getSearchRouting(), nullValue());
        existsResponse = admin().indices().prepareAliasesExist("foo")
                .addIndices("*bac").get();
        assertThat(existsResponse.exists(), equalTo(true));

        logger.info("--> getting foo for index foobar");
        getResponse = admin().indices().prepareGetAliases("foo").addIndices("foobar").get();
        assertThat(getResponse, notNullValue());
        assertThat(getResponse.getAliases().size(), equalTo(1));
        assertThat(getResponse.getAliases().get("foobar").get(0), notNullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).alias(), equalTo("foo"));
        assertThat(getResponse.getAliases().get("foobar").get(0).getFilter(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getIndexRouting(), nullValue());
        assertThat(getResponse.getAliases().get("foobar").get(0).getSearchRouting(), nullValue());
        existsResponse = admin().indices().prepareAliasesExist("foo")
                .addIndices("foobar").get();
        assertThat(existsResponse.exists(), equalTo(true));

        // alias at work again
        logger.info("--> getting * for index *bac");
        getResponse = admin().indices().prepareGetAliases("*").addIndices("*bac").get();
        assertThat(getResponse, notNullValue());
        assertThat(getResponse.getAliases().size(), equalTo(2));
        assertThat(getResponse.getAliases().get("foobar").size(), equalTo(4));
        assertThat(getResponse.getAliases().get("bazbar").size(), equalTo(2));
        existsResponse = admin().indices().prepareAliasesExist("*")
                .addIndices("*bac").get();
        assertThat(existsResponse.exists(), equalTo(true));

        assertAcked(admin().indices().prepareAliases()
                .removeAlias("foobar", "foo"));

        getResponse = admin().indices().prepareGetAliases("foo").addIndices("foobar").get();
        assertThat(getResponse.getAliases().isEmpty(), equalTo(true));
        existsResponse = admin().indices().prepareAliasesExist("foo").addIndices("foobar").get();
        assertThat(existsResponse.exists(), equalTo(false));
    }

    public void testAddAliasNullWithoutExistingIndices() {
        try {
            assertAcked(admin().indices().prepareAliases().addAliasAction(AliasAction.newAddAliasAction(null, "alias1")));
            fail("create alias should have failed due to null index");
        } catch (IllegalArgumentException e) {
            assertThat("Exception text does not contain \"Alias action [add]: [index] may not be empty string\"",
                    e.getMessage(), containsString("Alias action [add]: [index] may not be empty string"));
        }
    }

    public void testAddAliasNullWithExistingIndices() throws Exception {
        logger.info("--> creating index [test]");
        createIndex("test");
        ensureGreen();

        logger.info("--> aliasing index [null] with [empty-alias]");

        try {
            assertAcked(admin().indices().prepareAliases().addAlias((String) null, "empty-alias"));
            fail("create alias should have failed due to null index");
        } catch (IllegalArgumentException e) {
            assertThat("Exception text does not contain \"Alias action [add]: [index] may not be empty string\"",
                    e.getMessage(), containsString("Alias action [add]: [index] may not be empty string"));
        }
    }

    public void testAddAliasEmptyIndex() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newAddAliasAction("", "alias1")).get();
            fail("Expected ActionRequestValidationException");
        } catch (ActionRequestValidationException e) {
            assertThat(e.getMessage(), containsString("[index] may not be empty string"));
        }
    }

    public void testAddAliasNullAlias() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newAddAliasAction("index1", null)).get();
            fail("Expected ActionRequestValidationException");
        } catch (ActionRequestValidationException e) {
            assertThat(e.getMessage(), containsString("requires an [alias] to be set"));
        }
    }

    public void testAddAliasEmptyAlias() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newAddAliasAction("index1", "")).get();
            fail("Expected ActionRequestValidationException");
        } catch (ActionRequestValidationException e) {
            assertThat(e.getMessage(), containsString("requires an [alias] to be set"));
        }
    }

    public void testAddAliasNullAliasNullIndex() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newAddAliasAction(null, null)).get();
            fail("Should throw " + ActionRequestValidationException.class.getSimpleName());
        } catch (ActionRequestValidationException e) {
            assertThat(e.validationErrors(), notNullValue());
            assertThat(e.validationErrors().size(), equalTo(2));
        }
    }

    public void testAddAliasEmptyAliasEmptyIndex() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newAddAliasAction("", "")).get();
            fail("Should throw " + ActionRequestValidationException.class.getSimpleName());
        } catch (ActionRequestValidationException e) {
            assertThat(e.validationErrors(), notNullValue());
            assertThat(e.validationErrors().size(), equalTo(2));
        }
    }

    public void testRemoveAliasNullIndex() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newRemoveAliasAction(null, "alias1")).get();
            fail("Expected ActionRequestValidationException");
        } catch (ActionRequestValidationException e) {
            assertThat(e.getMessage(), containsString("[index] may not be empty string"));
        }
    }

    public void testRemoveAliasEmptyIndex() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newRemoveAliasAction("", "alias1")).get();
            fail("Expected ActionRequestValidationException");
        } catch (ActionRequestValidationException e) {
            assertThat(e.getMessage(), containsString("[index] may not be empty string"));
        }
    }

    public void testRemoveAliasNullAlias() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newRemoveAliasAction("index1", null)).get();
            fail("Expected ActionRequestValidationException");
        } catch (ActionRequestValidationException e) {
            assertThat(e.getMessage(), containsString("[alias] may not be empty string"));
        }
    }

    public void testRemoveAliasEmptyAlias() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newRemoveAliasAction("index1", "")).get();
            fail("Expected ActionRequestValidationException");
        } catch (ActionRequestValidationException e) {
            assertThat(e.getMessage(), containsString("[alias] may not be empty string"));
        }
    }

    public void testRemoveAliasNullAliasNullIndex() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newRemoveAliasAction(null, null)).get();
            fail("Should throw " + ActionRequestValidationException.class.getSimpleName());
        } catch (ActionRequestValidationException e) {
            assertThat(e.validationErrors(), notNullValue());
            assertThat(e.validationErrors().size(), equalTo(2));
        }
    }

    public void testRemoveAliasEmptyAliasEmptyIndex() {
        try {
            admin().indices().prepareAliases().addAliasAction(AliasAction.newAddAliasAction("", "")).get();
            fail("Should throw " + ActionRequestValidationException.class.getSimpleName());
        } catch (ActionRequestValidationException e) {
            assertThat(e.validationErrors(), notNullValue());
            assertThat(e.validationErrors().size(), equalTo(2));
        }
    }

    public void testGetAllAliasesWorks() {
        createIndex("index1");
        createIndex("index2");

        ensureYellow();

        assertAcked(admin().indices().prepareAliases().addAlias("index1", "alias1").addAlias("index2", "alias2"));

        GetAliasesResponse response = admin().indices().prepareGetAliases().get();
        assertThat(response.getAliases(), hasKey("index1"));
        assertThat(response.getAliases(), hasKey("index1"));
    }

    public void testCreateIndexWithAliases() throws Exception {
        assertAcked(prepareCreate("test")
                .addMapping("type", "field", "type=string")
                .addAlias(new Alias("alias1"))
                .addAlias(new Alias("alias2").filter(QueryBuilders.missingQuery("field")))
                .addAlias(new Alias("alias3").indexRouting("index").searchRouting("search")));

        checkAliases();
    }

    public void testCreateIndexWithAliasesInSource() throws Exception {
        assertAcked(prepareCreate("test").setSource("{\n" +
                "    \"aliases\" : {\n" +
                "        \"alias1\" : {},\n" +
                "        \"alias2\" : {\"filter\" : {\"match_all\": {}}},\n" +
                "        \"alias3\" : { \"index_routing\" : \"index\", \"search_routing\" : \"search\"}\n" +
                "    }\n" +
                "}"));

        checkAliases();
    }

    public void testCreateIndexWithAliasesSource() throws Exception {
        assertAcked(prepareCreate("test")
                .addMapping("type", "field", "type=string")
                .setAliases("{\n" +
                        "        \"alias1\" : {},\n" +
                        "        \"alias2\" : {\"filter\" : {\"term\": {\"field\":\"value\"}}},\n" +
                        "        \"alias3\" : { \"index_routing\" : \"index\", \"search_routing\" : \"search\"}\n" +
                        "}"));

        checkAliases();
    }

    public void testCreateIndexWithAliasesFilterNotValid() {
        //non valid filter, invalid json
        CreateIndexRequestBuilder createIndexRequestBuilder = prepareCreate("test").addAlias(new Alias("alias2").filter("f"));

        try {
            createIndexRequestBuilder.get();
            fail("create index should have failed due to invalid alias filter");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("failed to parse filter for alias [alias2]"));
        }

        //valid json but non valid filter
        createIndexRequestBuilder = prepareCreate("test").addAlias(new Alias("alias2").filter("{ \"test\": {} }"));

        try {
            createIndexRequestBuilder.get();
            fail("create index should have failed due to invalid alias filter");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("failed to parse filter for alias [alias2]"));
        }
    }

    // Before 2.0 alias filters were parsed at alias creation time, in order
    // for filters to work correctly ES required that fields mentioned in those
    // filters exist in the mapping.
    // From 2.0 and higher alias filters are parsed at request time and therefor
    // fields mentioned in filters don't need to exist in the mapping.
    public void testAddAliasWithFilterNoMapping() throws Exception {
        assertAcked(prepareCreate("test"));
        client().admin().indices().prepareAliases()
                .addAlias("test", "a", QueryBuilders.termQuery("field1", "term"))
                .get();
        client().admin().indices().prepareAliases()
                .addAlias("test", "a", QueryBuilders.rangeQuery("field2").from(0).to(1))
                .get();
        client().admin().indices().prepareAliases()
                .addAlias("test", "a", QueryBuilders.matchAllQuery())
                .get();
    }

    public void testAliasFilterWithNowInRangeFilterAndQuery() throws Exception {
        assertAcked(prepareCreate("my-index").addMapping("my-type", "_timestamp", "enabled=true"));
        assertAcked(admin().indices().prepareAliases().addAlias("my-index", "filter1", rangeQuery("_timestamp").from("now-1d").to("now")));
        assertAcked(admin().indices().prepareAliases().addAlias("my-index", "filter2", rangeQuery("_timestamp").from("now-1d").to("now")));

        final int numDocs = scaledRandomIntBetween(5, 52);
        for (int i = 1; i <= numDocs; i++) {
            client().prepareIndex("my-index", "my-type").setCreate(true).setSource("{}").get();
            if (i % 2 == 0) {
                refresh();
                SearchResponse response = client().prepareSearch("filter1").get();
                assertHitCount(response, i);

                response = client().prepareSearch("filter2").get();
                assertHitCount(response, i);
            }
        }
    }

    public void testAliasesFilterWithHasChildQuery() throws Exception {
        assertAcked(prepareCreate("my-index")
                        .addMapping("parent")
                        .addMapping("child", "_parent", "type=parent")
        );
        client().prepareIndex("my-index", "parent", "1").setSource("{}").get();
        client().prepareIndex("my-index", "child", "2").setSource("{}").setParent("1").get();
        refresh();

        assertAcked(admin().indices().prepareAliases().addAlias("my-index", "filter1", hasChildQuery("child", matchAllQuery())));
        assertAcked(admin().indices().prepareAliases().addAlias("my-index", "filter2", hasParentQuery("parent", matchAllQuery())));

        SearchResponse response = client().prepareSearch("filter1").get();
        assertHitCount(response, 1);
        assertThat(response.getHits().getAt(0).id(), equalTo("1"));
        response = client().prepareSearch("filter2").get();
        assertHitCount(response, 1);
        assertThat(response.getHits().getAt(0).id(), equalTo("2"));
    }

    public void testAliasesWithBlocks() {
        createIndex("test");
        ensureGreen();

        for (String block : Arrays.asList(SETTING_BLOCKS_READ, SETTING_BLOCKS_WRITE)) {
            try {
                enableIndexBlock("test", block);

                assertAcked(admin().indices().prepareAliases().addAlias("test", "alias1").addAlias("test", "alias2"));
                assertAcked(admin().indices().prepareAliases().removeAlias("test", "alias1"));
                assertThat(admin().indices().prepareGetAliases("alias2").execute().actionGet().getAliases().get("test").size(), equalTo(1));
                assertThat(admin().indices().prepareAliasesExist("alias2").get().exists(), equalTo(true));
            } finally {
                disableIndexBlock("test", block);
            }
        }

        try {
            enableIndexBlock("test", SETTING_READ_ONLY);

            assertBlocked(admin().indices().prepareAliases().addAlias("test", "alias3"), INDEX_READ_ONLY_BLOCK);
            assertBlocked(admin().indices().prepareAliases().removeAlias("test", "alias2"), INDEX_READ_ONLY_BLOCK);
            assertThat(admin().indices().prepareGetAliases("alias2").execute().actionGet().getAliases().get("test").size(), equalTo(1));
            assertThat(admin().indices().prepareAliasesExist("alias2").get().exists(), equalTo(true));

        } finally {
            disableIndexBlock("test", SETTING_READ_ONLY);
        }

        try {
            enableIndexBlock("test", SETTING_BLOCKS_METADATA);

            assertBlocked(admin().indices().prepareAliases().addAlias("test", "alias3"), INDEX_METADATA_BLOCK);
            assertBlocked(admin().indices().prepareAliases().removeAlias("test", "alias2"), INDEX_METADATA_BLOCK);
            assertBlocked(admin().indices().prepareGetAliases("alias2"), INDEX_METADATA_BLOCK);
            assertBlocked(admin().indices().prepareAliasesExist("alias2"), INDEX_METADATA_BLOCK);

        } finally {
            disableIndexBlock("test", SETTING_BLOCKS_METADATA);
        }
    }

    private void checkAliases() {
        GetAliasesResponse getAliasesResponse = admin().indices().prepareGetAliases("alias1").get();
        assertThat(getAliasesResponse.getAliases().get("test").size(), equalTo(1));
        AliasMetaData aliasMetaData = getAliasesResponse.getAliases().get("test").get(0);
        assertThat(aliasMetaData.alias(), equalTo("alias1"));
        assertThat(aliasMetaData.filter(), nullValue());
        assertThat(aliasMetaData.indexRouting(), nullValue());
        assertThat(aliasMetaData.searchRouting(), nullValue());

        getAliasesResponse = admin().indices().prepareGetAliases("alias2").get();
        assertThat(getAliasesResponse.getAliases().get("test").size(), equalTo(1));
        aliasMetaData = getAliasesResponse.getAliases().get("test").get(0);
        assertThat(aliasMetaData.alias(), equalTo("alias2"));
        assertThat(aliasMetaData.filter(), notNullValue());
        assertThat(aliasMetaData.indexRouting(), nullValue());
        assertThat(aliasMetaData.searchRouting(), nullValue());

        getAliasesResponse = admin().indices().prepareGetAliases("alias3").get();
        assertThat(getAliasesResponse.getAliases().get("test").size(), equalTo(1));
        aliasMetaData = getAliasesResponse.getAliases().get("test").get(0);
        assertThat(aliasMetaData.alias(), equalTo("alias3"));
        assertThat(aliasMetaData.filter(), nullValue());
        assertThat(aliasMetaData.indexRouting(), equalTo("index"));
        assertThat(aliasMetaData.searchRouting(), equalTo("search"));
    }

    private void assertHits(SearchHits hits, String... ids) {
        assertThat(hits.totalHits(), equalTo((long) ids.length));
        Set<String> hitIds = new HashSet<>();
        for (SearchHit hit : hits.getHits()) {
            hitIds.add(hit.id());
        }
        assertThat(hitIds, containsInAnyOrder(ids));
    }

    private String source(String id, String nameValue) {
        return "{ \"id\" : \"" + id + "\", \"name\" : \"" + nameValue + "\" }";
    }
}
