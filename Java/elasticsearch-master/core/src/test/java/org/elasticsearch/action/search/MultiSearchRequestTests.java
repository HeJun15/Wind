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

package org.elasticsearch.action.search;

import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.MatchAllQueryParser;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.rest.action.search.RestMultiSearchAction;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.StreamsUtils;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class MultiSearchRequestTests extends ESTestCase {
    public void testSimpleAdd() throws Exception {
        IndicesQueriesRegistry registry = new IndicesQueriesRegistry(Settings.EMPTY, Collections.singleton(new MatchAllQueryParser()), new NamedWriteableRegistry());
        byte[] data = StreamsUtils.copyToBytesFromClasspath("/org/elasticsearch/action/search/simple-msearch1.json");
        MultiSearchRequest request = RestMultiSearchAction.parseRequest(new MultiSearchRequest(), new BytesArray(data), false, null, null,
                null, null, IndicesOptions.strictExpandOpenAndForbidClosed(), true, registry, ParseFieldMatcher.EMPTY);
        assertThat(request.requests().size(), equalTo(8));
        assertThat(request.requests().get(0).indices()[0], equalTo("test"));
        assertThat(request.requests().get(0).indicesOptions(), equalTo(IndicesOptions.fromOptions(true, true, true, true, IndicesOptions.strictExpandOpenAndForbidClosed())));
        assertThat(request.requests().get(0).types().length, equalTo(0));
        assertThat(request.requests().get(1).indices()[0], equalTo("test"));
        assertThat(request.requests().get(1).indicesOptions(), equalTo(IndicesOptions.fromOptions(false, true, true, true, IndicesOptions.strictExpandOpenAndForbidClosed())));
        assertThat(request.requests().get(1).types()[0], equalTo("type1"));
        assertThat(request.requests().get(2).indices()[0], equalTo("test"));
        assertThat(request.requests().get(2).indicesOptions(), equalTo(IndicesOptions.fromOptions(false, true, true, false, IndicesOptions.strictExpandOpenAndForbidClosed())));
        assertThat(request.requests().get(3).indices()[0], equalTo("test"));
        assertThat(request.requests().get(3).indicesOptions(), equalTo(IndicesOptions.fromOptions(true, true, true, true, IndicesOptions.strictExpandOpenAndForbidClosed())));
        assertThat(request.requests().get(4).indices()[0], equalTo("test"));
        assertThat(request.requests().get(4).indicesOptions(), equalTo(IndicesOptions.fromOptions(true, false, false, true, IndicesOptions.strictExpandOpenAndForbidClosed())));
        assertThat(request.requests().get(5).indices(), nullValue());
        assertThat(request.requests().get(5).types().length, equalTo(0));
        assertThat(request.requests().get(6).indices(), nullValue());
        assertThat(request.requests().get(6).types().length, equalTo(0));
        assertThat(request.requests().get(6).searchType(), equalTo(SearchType.DFS_QUERY_THEN_FETCH));
        assertThat(request.requests().get(7).indices(), nullValue());
        assertThat(request.requests().get(7).types().length, equalTo(0));
    }

    public void testSimpleAdd2() throws Exception {
    IndicesQueriesRegistry registry = new IndicesQueriesRegistry(Settings.EMPTY, Collections.singleton(new MatchAllQueryParser()), new NamedWriteableRegistry());
        byte[] data = StreamsUtils.copyToBytesFromClasspath("/org/elasticsearch/action/search/simple-msearch2.json");
        MultiSearchRequest request = RestMultiSearchAction.parseRequest(new MultiSearchRequest(), new BytesArray(data), false, null, null,
                null, null, IndicesOptions.strictExpandOpenAndForbidClosed(), true, registry, ParseFieldMatcher.EMPTY);
        assertThat(request.requests().size(), equalTo(5));
        assertThat(request.requests().get(0).indices()[0], equalTo("test"));
        assertThat(request.requests().get(0).types().length, equalTo(0));
        assertThat(request.requests().get(1).indices()[0], equalTo("test"));
        assertThat(request.requests().get(1).types()[0], equalTo("type1"));
        assertThat(request.requests().get(2).indices(), nullValue());
        assertThat(request.requests().get(2).types().length, equalTo(0));
        assertThat(request.requests().get(3).indices(), nullValue());
        assertThat(request.requests().get(3).types().length, equalTo(0));
        assertThat(request.requests().get(3).searchType(), equalTo(SearchType.DFS_QUERY_THEN_FETCH));
        assertThat(request.requests().get(4).indices(), nullValue());
        assertThat(request.requests().get(4).types().length, equalTo(0));
    }

    public void testSimpleAdd3() throws Exception {
        IndicesQueriesRegistry registry = new IndicesQueriesRegistry(Settings.EMPTY, Collections.singleton(new MatchAllQueryParser()), new NamedWriteableRegistry());
        byte[] data = StreamsUtils.copyToBytesFromClasspath("/org/elasticsearch/action/search/simple-msearch3.json");
        MultiSearchRequest request = RestMultiSearchAction.parseRequest(new MultiSearchRequest(), new BytesArray(data), false, null, null,
                null, null, IndicesOptions.strictExpandOpenAndForbidClosed(), true, registry, ParseFieldMatcher.EMPTY);
        assertThat(request.requests().size(), equalTo(4));
        assertThat(request.requests().get(0).indices()[0], equalTo("test0"));
        assertThat(request.requests().get(0).indices()[1], equalTo("test1"));
        assertThat(request.requests().get(1).indices()[0], equalTo("test2"));
        assertThat(request.requests().get(1).indices()[1], equalTo("test3"));
        assertThat(request.requests().get(1).types()[0], equalTo("type1"));
        assertThat(request.requests().get(2).indices()[0], equalTo("test4"));
        assertThat(request.requests().get(2).indices()[1], equalTo("test1"));
        assertThat(request.requests().get(2).types()[0], equalTo("type2"));
        assertThat(request.requests().get(2).types()[1], equalTo("type1"));
        assertThat(request.requests().get(3).indices(), nullValue());
        assertThat(request.requests().get(3).types().length, equalTo(0));
        assertThat(request.requests().get(3).searchType(), equalTo(SearchType.DFS_QUERY_THEN_FETCH));
    }

    public void testSimpleAdd4() throws Exception {
        IndicesQueriesRegistry registry = new IndicesQueriesRegistry(Settings.EMPTY, Collections.singleton(new MatchAllQueryParser()), new NamedWriteableRegistry());
        byte[] data = StreamsUtils.copyToBytesFromClasspath("/org/elasticsearch/action/search/simple-msearch4.json");
        MultiSearchRequest request = RestMultiSearchAction.parseRequest(new MultiSearchRequest(), new BytesArray(data), false, null, null,
                null, null, IndicesOptions.strictExpandOpenAndForbidClosed(), true, registry, ParseFieldMatcher.EMPTY);
        assertThat(request.requests().size(), equalTo(3));
        assertThat(request.requests().get(0).indices()[0], equalTo("test0"));
        assertThat(request.requests().get(0).indices()[1], equalTo("test1"));
        assertThat(request.requests().get(0).requestCache(), equalTo(true));
        assertThat(request.requests().get(0).preference(), nullValue());
        assertThat(request.requests().get(1).indices()[0], equalTo("test2"));
        assertThat(request.requests().get(1).indices()[1], equalTo("test3"));
        assertThat(request.requests().get(1).types()[0], equalTo("type1"));
        assertThat(request.requests().get(1).requestCache(), nullValue());
        assertThat(request.requests().get(1).preference(), equalTo("_local"));
        assertThat(request.requests().get(2).indices()[0], equalTo("test4"));
        assertThat(request.requests().get(2).indices()[1], equalTo("test1"));
        assertThat(request.requests().get(2).types()[0], equalTo("type2"));
        assertThat(request.requests().get(2).types()[1], equalTo("type1"));
        assertThat(request.requests().get(2).routing(), equalTo("123"));
    }

    public void testSimpleAdd5() throws Exception {
        IndicesQueriesRegistry registry = new IndicesQueriesRegistry(Settings.EMPTY, Collections.singleton(new MatchAllQueryParser()), new NamedWriteableRegistry());
        byte[] data = StreamsUtils.copyToBytesFromClasspath("/org/elasticsearch/action/search/simple-msearch5.json");
        MultiSearchRequest request = RestMultiSearchAction.parseRequest(new MultiSearchRequest(), new BytesArray(data), true, null, null,
                null, null, IndicesOptions.strictExpandOpenAndForbidClosed(), true, registry, ParseFieldMatcher.EMPTY);
        assertThat(request.requests().size(), equalTo(3));
        assertThat(request.requests().get(0).indices()[0], equalTo("test0"));
        assertThat(request.requests().get(0).indices()[1], equalTo("test1"));
        assertThat(request.requests().get(0).requestCache(), equalTo(true));
        assertThat(request.requests().get(0).preference(), nullValue());
        assertThat(request.requests().get(1).indices()[0], equalTo("test2"));
        assertThat(request.requests().get(1).indices()[1], equalTo("test3"));
        assertThat(request.requests().get(1).types()[0], equalTo("type1"));
        assertThat(request.requests().get(1).requestCache(), nullValue());
        assertThat(request.requests().get(1).preference(), equalTo("_local"));
        assertThat(request.requests().get(2).indices()[0], equalTo("test4"));
        assertThat(request.requests().get(2).indices()[1], equalTo("test1"));
        assertThat(request.requests().get(2).types()[0], equalTo("type2"));
        assertThat(request.requests().get(2).types()[1], equalTo("type1"));
        assertThat(request.requests().get(2).routing(), equalTo("123"));
        assertNotNull(request.requests().get(0).template());
        assertNotNull(request.requests().get(1).template());
        assertNotNull(request.requests().get(2).template());
        assertEquals(ScriptService.ScriptType.INLINE, request.requests().get(0).template().getType());
        assertEquals(ScriptService.ScriptType.INLINE, request.requests().get(1).template().getType());
        assertEquals(ScriptService.ScriptType.INLINE, request.requests().get(2).template().getType());
        assertEquals("{\"query\":{\"match_{{template}}\":{}}}", request.requests().get(0).template().getScript());
        assertEquals("{\"query\":{\"match_{{template}}\":{}}}", request.requests().get(1).template().getScript());
        assertEquals("{\"query\":{\"match_{{template}}\":{}}}", request.requests().get(2).template().getScript());
        assertEquals(1, request.requests().get(0).template().getParams().size());
        assertEquals(1, request.requests().get(1).template().getParams().size());
        assertEquals(1, request.requests().get(2).template().getParams().size());
    }

    public void testResponseErrorToXContent() throws IOException {
        MultiSearchResponse response = new MultiSearchResponse(new MultiSearchResponse.Item[]{new MultiSearchResponse.Item(null, new IllegalStateException("foobar")), new MultiSearchResponse.Item(null, new IllegalStateException("baaaaaazzzz"))});
        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertEquals("\"responses\"[{\"error\":{\"root_cause\":[{\"type\":\"illegal_state_exception\",\"reason\":\"foobar\"}],\"type\":\"illegal_state_exception\",\"reason\":\"foobar\"}},{\"error\":{\"root_cause\":[{\"type\":\"illegal_state_exception\",\"reason\":\"baaaaaazzzz\"}],\"type\":\"illegal_state_exception\",\"reason\":\"baaaaaazzzz\"}}]",
                builder.string());
    }
}
