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

package org.elasticsearch.index.mapper.murmur3;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.plugin.mapper.MapperMurmur3Plugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.InternalTestCluster;
import org.elasticsearch.test.hamcrest.ElasticsearchAssertions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.TEST, numDataNodes = 0)
@LuceneTestCase.SuppressFileSystems("ExtrasFS")
public class Murmur3FieldMapperUpgradeTests extends ESIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singleton(MapperMurmur3Plugin.class);
    }

    public void testUpgradeOldMapping() throws IOException, ExecutionException, InterruptedException {
        final String indexName = "index-mapper-murmur3-2.0.0";
        InternalTestCluster.Async<String> master = internalCluster().startNodeAsync();
        Path unzipDir = createTempDir();
        Path unzipDataDir = unzipDir.resolve("data");
        Path backwardsIndex = getBwcIndicesPath().resolve(indexName + ".zip");
        try (InputStream stream = Files.newInputStream(backwardsIndex)) {
            TestUtil.unzip(stream, unzipDir);
        }
        assertTrue(Files.exists(unzipDataDir));

        Path dataPath = createTempDir();
        Settings settings = Settings.builder()
                .put("path.data", dataPath)
                .build();
        final String node = internalCluster().startDataOnlyNode(settings); // workaround for dangling index loading issue when node is master
        Path[] nodePaths = internalCluster().getInstance(NodeEnvironment.class, node).nodeDataPaths();
        assertEquals(1, nodePaths.length);
        dataPath = nodePaths[0].resolve(NodeEnvironment.INDICES_FOLDER);
        assertFalse(Files.exists(dataPath));
        Path src = unzipDataDir.resolve(indexName + "/nodes/0/indices");
        Files.move(src, dataPath);

        master.get();
        // force reloading dangling indices with a cluster state republish
        client().admin().cluster().prepareReroute().get();
        ensureGreen(indexName);
        final SearchResponse countResponse = client().prepareSearch(indexName).setSize(0).get();
        ElasticsearchAssertions.assertHitCount(countResponse, 3L);

        final SearchResponse cardinalityResponse = client().prepareSearch(indexName).addAggregation(
                AggregationBuilders.cardinality("card").field("foo.hash")).get();
        Cardinality cardinality = cardinalityResponse.getAggregations().get("card");
        assertEquals(3L, cardinality.getValue());
    }
}
