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
package org.elasticsearch.plugins;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.http.HttpServerTransport;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.elasticsearch.test.ESIntegTestCase.Scope;
import org.elasticsearch.test.rest.client.http.HttpRequestBuilder;
import org.elasticsearch.test.rest.client.http.HttpResponse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.elasticsearch.rest.RestStatus.FORBIDDEN;
import static org.elasticsearch.rest.RestStatus.MOVED_PERMANENTLY;
import static org.elasticsearch.rest.RestStatus.NOT_FOUND;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasStatus;
import static org.hamcrest.Matchers.containsString;

/**
 * We want to test site plugins
 */
@ClusterScope(scope = Scope.SUITE, numDataNodes = 1)
public class SitePluginIT extends ESIntegTestCase {
    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        Path pluginDir = getDataPath("/org/elasticsearch/test_plugins");
        return settingsBuilder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("path.plugins", pluginDir.toAbsolutePath())
                .put("force.http.enabled", true)
                .build();
    }

    @Override
    public HttpRequestBuilder httpClient() {
        RequestConfig.Builder builder = RequestConfig.custom().setRedirectsEnabled(false);
        CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(builder.build()).build();
        return new HttpRequestBuilder(httpClient).httpTransport(internalCluster().getDataNodeInstance(HttpServerTransport.class));
    }

    public void testRedirectSitePlugin() throws Exception {
        // We use an HTTP Client to test redirection
        HttpResponse response = httpClient().method("GET").path("/_plugin/dummy").execute();
        assertThat(response, hasStatus(MOVED_PERMANENTLY));
        assertThat(response.getBody(), containsString("/_plugin/dummy/"));

        // We test the real URL
        response = httpClient().method("GET").path("/_plugin/dummy/").execute();
        assertThat(response, hasStatus(OK));
        assertThat(response.getBody(), containsString("<title>Dummy Site Plugin</title>"));
    }

    /**
     * Test direct access to an existing file (index.html)
     */
    public void testAnyPage() throws Exception {
        HttpResponse response = httpClient().path("/_plugin/dummy/index.html").execute();
        assertThat(response, hasStatus(OK));
        assertThat(response.getBody(), containsString("<title>Dummy Site Plugin</title>"));
    }

    /**
     * Test normalizing of path
     */
    public void testThatPathsAreNormalized() throws Exception {
        // more info: https://www.owasp.org/index.php/Path_Traversal
        List<String> notFoundUris = new ArrayList<>();
        notFoundUris.add("/_plugin/dummy/../../../../../log4j.properties");
        notFoundUris.add("/_plugin/dummy/../../../../../%00log4j.properties");
        notFoundUris.add("/_plugin/dummy/..%c0%af..%c0%af..%c0%af..%c0%af..%c0%aflog4j.properties");
        notFoundUris.add("/_plugin/dummy/%2E%2E/%2E%2E/%2E%2E/%2E%2E/index.html");
        notFoundUris.add("/_plugin/dummy/%2e%2e/%2e%2e/%2e%2e/%2e%2e/index.html");
        notFoundUris.add("/_plugin/dummy/%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2findex.html");
        notFoundUris.add("/_plugin/dummy/%2E%2E/%2E%2E/%2E%2E/%2E%2E/index.html");
        notFoundUris.add("/_plugin/dummy/..%5C..%5C..%5C..%5C..%5Clog4j.properties");

        for (String uri : notFoundUris) {
            HttpResponse response = httpClient().path(uri).execute();
            String message = String.format(Locale.ROOT, "URI [%s] expected to be not found", uri);
            assertThat(message, response, hasStatus(NOT_FOUND));
        }

        // using relative path inside of the plugin should work
        HttpResponse response = httpClient().path("/_plugin/dummy/dir1/../dir1/../index.html").execute();
        assertThat(response, hasStatus(OK));
        assertThat(response.getBody(), containsString("<title>Dummy Site Plugin</title>"));
    }

    /**
     * Test case for #4845: https://github.com/elasticsearch/elasticsearch/issues/4845
     * Serving _site plugins do not pick up on index.html for sub directories
     */
    public void testWelcomePageInSubDirs() throws Exception {
        HttpResponse response = httpClient().path("/_plugin/subdir/dir/").execute();
        assertThat(response, hasStatus(OK));
        assertThat(response.getBody(), containsString("<title>Dummy Site Plugin (subdir)</title>"));

        response = httpClient().path("/_plugin/subdir/dir_without_index/").execute();
        assertThat(response, hasStatus(FORBIDDEN));

        response = httpClient().path("/_plugin/subdir/dir_without_index/page.html").execute();
        assertThat(response, hasStatus(OK));
        assertThat(response.getBody(), containsString("<title>Dummy Site Plugin (page)</title>"));
    }
}
