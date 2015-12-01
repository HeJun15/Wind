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
package org.elasticsearch.http.netty;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.http.HttpServerTransport;
import org.elasticsearch.node.Node;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.elasticsearch.test.ESIntegTestCase.Scope;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.elasticsearch.http.netty.NettyHttpClient.returnOpaqueIds;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

/**
 *
 */
@ClusterScope(scope = Scope.TEST, numDataNodes = 1)
public class NettyPipeliningDisabledIT extends ESIntegTestCase {
    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return settingsBuilder().put(super.nodeSettings(nodeOrdinal)).put(Node.HTTP_ENABLED, true).put("http.pipelining", false).build();
    }

    public void testThatNettyHttpServerDoesNotSupportPipelining() throws Exception {
        ensureGreen();
        List<String> requests = Arrays.asList("/", "/_nodes/stats", "/", "/_cluster/state", "/", "/_nodes", "/");

        HttpServerTransport httpServerTransport = internalCluster().getInstance(HttpServerTransport.class);
        InetSocketTransportAddress inetSocketTransportAddress = (InetSocketTransportAddress) randomFrom(httpServerTransport.boundAddress().boundAddresses());

        try (NettyHttpClient nettyHttpClient = new NettyHttpClient()) {
            Collection<HttpResponse> responses = nettyHttpClient.sendRequests(inetSocketTransportAddress.address(), requests.toArray(new String[]{}));
            assertThat(responses, hasSize(requests.size()));

            List<String> opaqueIds = new ArrayList<>(returnOpaqueIds(responses));

            assertResponsesOutOfOrder(opaqueIds);
        }
    }

    /**
     * checks if all responses are there, but also tests that they are out of order because pipelining is disabled
     */
    private void assertResponsesOutOfOrder(List<String> opaqueIds) {
        String message = String.format(Locale.ROOT, "Expected returned http message ids to be in any order of: %s", opaqueIds);
        assertThat(message, opaqueIds, containsInAnyOrder("0", "1", "2", "3", "4", "5", "6"));
    }
}
