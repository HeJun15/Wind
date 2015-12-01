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
package org.elasticsearch.indices.state;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.admin.indices.close.CloseIndexResponse;
import org.elasticsearch.action.admin.indices.close.TransportCloseIndexAction;
import org.elasticsearch.action.support.DestructiveOperations;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.elasticsearch.test.ESIntegTestCase.Scope;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@ClusterScope(scope=Scope.TEST, numDataNodes=2)
public class CloseIndexDisableCloseAllIT extends ESIntegTestCase {
    // Combined multiple tests into one, because cluster scope is test.
    // The cluster scope is test b/c we can't clear cluster settings.
    public void testCloseAllRequiresName() {
        Settings clusterSettings = Settings.builder()
                .put(DestructiveOperations.REQUIRES_NAME, true)
                .build();
        assertAcked(client().admin().cluster().prepareUpdateSettings().setTransientSettings(clusterSettings));
        createIndex("test1", "test2", "test3");
        ClusterHealthResponse healthResponse = client().admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
        assertThat(healthResponse.isTimedOut(), equalTo(false));

        // Close all explicitly
        try {
            client().admin().indices().prepareClose("_all").execute().actionGet();
            fail();
        } catch (IllegalArgumentException e) {
        }

        // Close all wildcard
        try {
            client().admin().indices().prepareClose("*").execute().actionGet();
            fail();
        } catch (IllegalArgumentException e) {
        }

        // Close all wildcard
        try {
            client().admin().indices().prepareClose("test*").execute().actionGet();
            fail();
        } catch (IllegalArgumentException e) {
        }

        // Close all wildcard
        try {
            client().admin().indices().prepareClose("*", "-test1").execute().actionGet();
            fail();
        } catch (IllegalArgumentException e) {
        }

        // Close all wildcard
        try {
            client().admin().indices().prepareClose("*", "-test1", "+test1").execute().actionGet();
            fail();
        } catch (IllegalArgumentException e) {
        }

        CloseIndexResponse closeIndexResponse = client().admin().indices().prepareClose("test3", "test2").execute().actionGet();
        assertThat(closeIndexResponse.isAcknowledged(), equalTo(true));
        assertIndexIsClosed("test2", "test3");

        // disable closing
        Client client = client();
        createIndex("test_no_close");
        healthResponse = client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
        assertThat(healthResponse.isTimedOut(), equalTo(false));
        client().admin().cluster().prepareUpdateSettings().setTransientSettings(Settings.builder().put(TransportCloseIndexAction.SETTING_CLUSTER_INDICES_CLOSE_ENABLE, false)).get();

        try {
            client.admin().indices().prepareClose("test_no_close").execute().actionGet();
            fail("exception expected");
        } catch (IllegalStateException ex) {
            assertEquals(ex.getMessage(), "closing indices is disabled - set [cluster.indices.close.enable: true] to enable it. NOTE: closed indices still consume a significant amount of diskspace");
        }
    }

    private void assertIndexIsClosed(String... indices) {
        checkIndexState(IndexMetaData.State.CLOSE, indices);
    }

    private void checkIndexState(IndexMetaData.State state, String... indices) {
        ClusterStateResponse clusterStateResponse = client().admin().cluster().prepareState().execute().actionGet();
        for (String index : indices) {
            IndexMetaData indexMetaData = clusterStateResponse.getState().metaData().indices().get(index);
            assertThat(indexMetaData, notNullValue());
            assertThat(indexMetaData.getState(), equalTo(state));
        }
    }
}
