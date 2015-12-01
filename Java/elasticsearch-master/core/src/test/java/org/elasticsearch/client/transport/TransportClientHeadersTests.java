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

package org.elasticsearch.client.transport;

import org.elasticsearch.Version;
import org.elasticsearch.action.GenericAction;
import org.elasticsearch.action.admin.cluster.node.liveness.LivenessResponse;
import org.elasticsearch.action.admin.cluster.node.liveness.TransportLivenessAction;
import org.elasticsearch.action.admin.cluster.state.ClusterStateAction;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.client.AbstractClientHeadersTestCase;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.LocalTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.ConnectTransportException;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.TransportException;
import org.elasticsearch.transport.TransportModule;
import org.elasticsearch.transport.TransportRequest;
import org.elasticsearch.transport.TransportRequestOptions;
import org.elasticsearch.transport.TransportResponse;
import org.elasticsearch.transport.TransportResponseHandler;
import org.elasticsearch.transport.TransportService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 *
 */
public class TransportClientHeadersTests extends AbstractClientHeadersTestCase {

    private static final LocalTransportAddress address = new LocalTransportAddress("test");

    @Override
    protected Client buildClient(Settings headersSettings, GenericAction[] testedActions) {
        TransportClient client = TransportClient.builder()
            .settings(Settings.builder()
                .put("client.transport.sniff", false)
                .put("node.name", "transport_client_" + this.getTestName())
                .put(headersSettings)
                .build())
            .addPlugin(InternalTransportService.TestPlugin.class).build();

        client.addTransportAddress(address);
        return client;
    }

    public void testWithSniffing() throws Exception {
        TransportClient client = TransportClient.builder()
            .settings(Settings.builder()
                .put("client.transport.sniff", true)
                .put("cluster.name", "cluster1")
                .put("node.name", "transport_client_" + this.getTestName() + "_1")
                .put("client.transport.nodes_sampler_interval", "1s")
                .put(HEADER_SETTINGS)
                .put("path.home", createTempDir().toString()).build())
            .addPlugin(InternalTransportService.TestPlugin.class)
            .build();

        try {
            client.addTransportAddress(address);

            InternalTransportService service = (InternalTransportService) client.injector.getInstance(TransportService.class);

            if (!service.clusterStateLatch.await(5, TimeUnit.SECONDS)) {
                fail("takes way too long to get the cluster state");
            }

            assertThat(client.connectedNodes().size(), is(1));
            assertThat(client.connectedNodes().get(0).getAddress(), is((TransportAddress) address));
        } finally {
            client.close();
        }

    }

    public static class InternalTransportService extends TransportService {

        public static class TestPlugin extends Plugin {
            @Override
            public String name() {
                return "mock-transport-service";
            }
            @Override
            public String description() {
                return "a mock transport service";
            }
            public void onModule(TransportModule transportModule) {
                transportModule.addTransportService("internal", InternalTransportService.class);
            }
            @Override
            public Settings additionalSettings() {
                return Settings.builder().put(TransportModule.TRANSPORT_SERVICE_TYPE_KEY, "internal").build();
            }
        }

        CountDownLatch clusterStateLatch = new CountDownLatch(1);

        @Inject
        public InternalTransportService(Settings settings, Transport transport, ThreadPool threadPool) {
            super(settings, transport, threadPool);
        }

        @Override @SuppressWarnings("unchecked")
        public <T extends TransportResponse> void sendRequest(DiscoveryNode node, String action, TransportRequest request, TransportRequestOptions options, TransportResponseHandler<T> handler) {
            if (TransportLivenessAction.NAME.equals(action)) {
                assertHeaders(request);
                ((TransportResponseHandler<LivenessResponse>) handler).handleResponse(new LivenessResponse(ClusterName.DEFAULT, node));
                return;
            }
            if (ClusterStateAction.NAME.equals(action)) {
                assertHeaders(request);
                ClusterName cluster1 = new ClusterName("cluster1");
                ((TransportResponseHandler<ClusterStateResponse>) handler).handleResponse(new ClusterStateResponse(cluster1, state(cluster1)));
                clusterStateLatch.countDown();
                return;
            }

            handler.handleException(new TransportException("", new InternalException(action, request)));
        }

        @Override
        public boolean nodeConnected(DiscoveryNode node) {
            assertThat((LocalTransportAddress) node.getAddress(), equalTo(address));
            return true;
        }

        @Override
        public void connectToNode(DiscoveryNode node) throws ConnectTransportException {
            assertThat((LocalTransportAddress) node.getAddress(), equalTo(address));
        }
    }

    private static ClusterState state(ClusterName clusterName) {
        ClusterState.Builder builder = ClusterState.builder(clusterName);
        builder.nodes(DiscoveryNodes.builder().put(new DiscoveryNode("node_id", address, Version.CURRENT)));
        return builder.build();
    }

}
