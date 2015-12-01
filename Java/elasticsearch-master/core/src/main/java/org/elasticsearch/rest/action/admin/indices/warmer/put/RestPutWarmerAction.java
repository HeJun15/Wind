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
package org.elasticsearch.rest.action.admin.indices.warmer.put;

import org.elasticsearch.action.admin.indices.warmer.put.PutWarmerRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.support.AcknowledgedRestListener;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestRequest.Method.PUT;

/**
 */
public class RestPutWarmerAction extends BaseRestHandler {

    private final IndicesQueriesRegistry queryRegistry;

    @Inject
    public RestPutWarmerAction(Settings settings, RestController controller, Client client, IndicesQueriesRegistry queryRegistry) {
        super(settings, controller, client);
        this.queryRegistry = queryRegistry;
        controller.registerHandler(PUT, "/_warmer/{name}", this);
        controller.registerHandler(PUT, "/{index}/_warmer/{name}", this);
        controller.registerHandler(PUT, "/{index}/{type}/_warmer/{name}", this);

        controller.registerHandler(PUT, "/_warmers/{name}", this);
        controller.registerHandler(PUT, "/{index}/_warmers/{name}", this);
        controller.registerHandler(PUT, "/{index}/{type}/_warmers/{name}", this);

        controller.registerHandler(POST, "/_warmer/{name}", this);
        controller.registerHandler(POST, "/{index}/_warmer/{name}", this);
        controller.registerHandler(POST, "/{index}/{type}/_warmer/{name}", this);

        controller.registerHandler(POST, "/_warmers/{name}", this);
        controller.registerHandler(POST, "/{index}/_warmers/{name}", this);
        controller.registerHandler(POST, "/{index}/{type}/_warmers/{name}", this);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel, final Client client) throws IOException {
        PutWarmerRequest putWarmerRequest = new PutWarmerRequest(request.param("name"));

        BytesReference sourceBytes = RestActions.getRestContent(request);
        SearchSourceBuilder source = RestActions.getRestSearchSource(sourceBytes, queryRegistry, parseFieldMatcher);
        SearchRequest searchRequest = new SearchRequest(Strings.splitStringByCommaToArray(request.param("index")))
                .types(Strings.splitStringByCommaToArray(request.param("type")))
                .requestCache(request.paramAsBoolean("request_cache", null)).source(source);
        searchRequest.indicesOptions(IndicesOptions.fromRequest(request, searchRequest.indicesOptions()));
        putWarmerRequest.searchRequest(searchRequest);
        putWarmerRequest.timeout(request.paramAsTime("timeout", putWarmerRequest.timeout()));
        putWarmerRequest.masterNodeTimeout(request.paramAsTime("master_timeout", putWarmerRequest.masterNodeTimeout()));
        client.admin().indices().putWarmer(putWarmerRequest, new AcknowledgedRestListener<>(channel));
    }
}
