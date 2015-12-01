/*
 * Copyright 2013 Lukas Vlcek and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.demo;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IndexAndSearchTest extends BaseTestSupport {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    private Settings settings;

    @Before
    public void prepare() throws IOException {
        String tempFolderName = testFolder.newFolder(IndexAndSearchTest.class.getName()).getCanonicalPath();
        settings = settingsBuilder()
                .put("index.store.type", "memory")
                .put("gateway.type", "none")
                .put("path.data", tempFolderName)
                .build();
    }

    /**
     * Index and search data. To make sure we can search for data that has been indexed just now call the refresh.
     * By default the refresh is executed every second.
     *
     * @throws IOException
     */
    @Test public void shouldIndexAndSearchRightAfterRefresh() throws IOException {

        // For unit tests it is recommended to use local node.
        // This is to ensure that your node will never join existing cluster on the network.
        Node node = NodeBuilder.nodeBuilder()
                .settings(settings)
                .local(true)
                .node();

        // get Client
        Client client = node.client();

        // Index some data
        client.prepareIndex("house", "room", "1").setSource(createSource("livingroom", "red")).execute().actionGet();
        client.prepareIndex("house", "room", "2").setSource(createSource("familyroom", "white")).execute().actionGet();
        client.prepareIndex("house", "room", "3").setSource(createSource("kitchen", "blue")).execute().actionGet();
        client.prepareIndex("house", "room", "4").setSource(createSource("bathroom", "white")).execute().actionGet();
        client.prepareIndex("house", "room", "5").setSource(createSource("garage", "blue")).execute().actionGet();

        // Refresh index reader
        client.admin().indices().refresh(Requests.refreshRequest("_all")).actionGet();

        // Prepare and execute query
        QueryBuilder queryBuilder = QueryBuilders.termQuery("color", "white");

        SearchResponse resp = client.prepareSearch("house")
                .setTypes("room")
//                .setSearchType(SearchType.DEFAULT)
                .setQuery(queryBuilder)
//                .setFrom(0).setSize(60).setExplain(true)
                .execute()
                .actionGet();

        // Make sure we got back expected data
        List<String> expected = new ArrayList<String>();
        expected.add("2");
        expected.add("4");

        assertEquals(expected.size(), resp.getHits().getTotalHits());

        for (SearchHit hit : resp.getHits().getHits()) {
            assertTrue(expected.contains(hit.id()));
        }

        // cleanup
        client.close();
        node.close();

        assertTrue(node.isClosed());
    }
}
