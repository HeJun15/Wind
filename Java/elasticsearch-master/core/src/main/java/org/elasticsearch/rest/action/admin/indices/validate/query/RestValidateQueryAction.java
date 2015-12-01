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

package org.elasticsearch.rest.action.admin.indices.validate.query;

import org.elasticsearch.action.admin.indices.validate.query.QueryExplanation;
import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryRequest;
import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.rest.*;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.rest.action.support.RestBuilderListener;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestActions.buildBroadcastShardsHeader;

/**
 *
 */
public class RestValidateQueryAction extends BaseRestHandler {

    private final IndicesQueriesRegistry indicesQueriesRegistry;

    @Inject
    public RestValidateQueryAction(Settings settings, RestController controller, Client client, IndicesQueriesRegistry indicesQueriesRegistry) {
        super(settings, controller, client);
        controller.registerHandler(GET, "/_validate/query", this);
        controller.registerHandler(POST, "/_validate/query", this);
        controller.registerHandler(GET, "/{index}/_validate/query", this);
        controller.registerHandler(POST, "/{index}/_validate/query", this);
        controller.registerHandler(GET, "/{index}/{type}/_validate/query", this);
        controller.registerHandler(POST, "/{index}/{type}/_validate/query", this);
        this.indicesQueriesRegistry = indicesQueriesRegistry;
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel, final Client client) throws Exception {
        ValidateQueryRequest validateQueryRequest = new ValidateQueryRequest(Strings.splitStringByCommaToArray(request.param("index")));
        validateQueryRequest.indicesOptions(IndicesOptions.fromRequest(request, validateQueryRequest.indicesOptions()));
        validateQueryRequest.explain(request.paramAsBoolean("explain", false));
        if (RestActions.hasBodyContent(request)) {
            try {
                validateQueryRequest.query(RestActions.getQueryContent(RestActions.getRestContent(request), indicesQueriesRegistry, parseFieldMatcher));
            } catch(ParsingException e) {
                channel.sendResponse(buildErrorResponse(channel.newBuilder(), e.getDetailedMessage(), validateQueryRequest.explain()));
                return;
            } catch(Exception e) {
                channel.sendResponse(buildErrorResponse(channel.newBuilder(), e.getMessage(), validateQueryRequest.explain()));
                return;
            }
        } else {
            QueryBuilder<?> queryBuilder = RestActions.urlParamsToQueryBuilder(request);
            if (queryBuilder != null) {
                validateQueryRequest.query(queryBuilder);
            }
        }
        validateQueryRequest.types(Strings.splitStringByCommaToArray(request.param("type")));
        validateQueryRequest.rewrite(request.paramAsBoolean("rewrite", false));

        client.admin().indices().validateQuery(validateQueryRequest, new RestBuilderListener<ValidateQueryResponse>(channel) {
            @Override
            public RestResponse buildResponse(ValidateQueryResponse response, XContentBuilder builder) throws Exception {
                builder.startObject();
                builder.field(VALID_FIELD, response.isValid());
                buildBroadcastShardsHeader(builder, request, response);
                if (response.getQueryExplanation() != null && !response.getQueryExplanation().isEmpty()) {
                    builder.startArray(EXPLANATIONS_FIELD);
                    for (QueryExplanation explanation : response.getQueryExplanation()) {
                        builder.startObject();
                        if (explanation.getIndex() != null) {
                            builder.field(INDEX_FIELD, explanation.getIndex(), XContentBuilder.FieldCaseConversion.NONE);
                        }
                        builder.field(VALID_FIELD, explanation.isValid());
                        if (explanation.getError() != null) {
                            builder.field(ERROR_FIELD, explanation.getError());
                        }
                        if (explanation.getExplanation() != null) {
                            builder.field(EXPLANATION_FIELD, explanation.getExplanation());
                        }
                        builder.endObject();
                    }
                    builder.endArray();
                }
                builder.endObject();
                return new BytesRestResponse(OK, builder);
            }
        });
    }

    private static BytesRestResponse buildErrorResponse(XContentBuilder builder, String error, boolean explain) throws IOException {
        builder.startObject();
        builder.field(VALID_FIELD, false);
        if (explain) {
            builder.field(ERROR_FIELD, error);
        }
        builder.endObject();
        return new BytesRestResponse(OK, builder);
    }

    private static final XContentBuilderString INDEX_FIELD = new XContentBuilderString("index");
    private static final XContentBuilderString VALID_FIELD = new XContentBuilderString("valid");
    private static final XContentBuilderString EXPLANATIONS_FIELD = new XContentBuilderString("explanations");
    private static final XContentBuilderString ERROR_FIELD = new XContentBuilderString("error");
    private static final XContentBuilderString EXPLANATION_FIELD = new XContentBuilderString("explanation");
}
