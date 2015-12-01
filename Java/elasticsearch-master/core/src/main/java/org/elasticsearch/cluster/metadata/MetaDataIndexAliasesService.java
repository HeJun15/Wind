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

package org.elasticsearch.cluster.metadata;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesClusterStateUpdateRequest;
import org.elasticsearch.cluster.AckedClusterStateUpdateTask;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ack.ClusterStateUpdateResponse;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.NodeServicesProvider;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.indices.IndicesService;

import java.util.*;

/**
 * Service responsible for submitting add and remove aliases requests
 */
public class MetaDataIndexAliasesService extends AbstractComponent {

    private final ClusterService clusterService;

    private final IndicesService indicesService;

    private final AliasValidator aliasValidator;

    private final NodeServicesProvider nodeServicesProvider;

    @Inject
    public MetaDataIndexAliasesService(Settings settings, ClusterService clusterService, IndicesService indicesService, AliasValidator aliasValidator, NodeServicesProvider nodeServicesProvider) {
        super(settings);
        this.clusterService = clusterService;
        this.indicesService = indicesService;
        this.aliasValidator = aliasValidator;
        this.nodeServicesProvider = nodeServicesProvider;
    }

    public void indicesAliases(final IndicesAliasesClusterStateUpdateRequest request, final ActionListener<ClusterStateUpdateResponse> listener) {
        clusterService.submitStateUpdateTask("index-aliases", new AckedClusterStateUpdateTask<ClusterStateUpdateResponse>(Priority.URGENT, request, listener) {
            @Override
            protected ClusterStateUpdateResponse newResponse(boolean acknowledged) {
                return new ClusterStateUpdateResponse(acknowledged);
            }

            @Override
            public ClusterState execute(final ClusterState currentState) {
                List<String> indicesToClose = new ArrayList<>();
                Map<String, IndexService> indices = new HashMap<>();
                try {
                    for (AliasAction aliasAction : request.actions()) {
                        aliasValidator.validateAliasAction(aliasAction, currentState.metaData());
                        if (!currentState.metaData().hasIndex(aliasAction.index())) {
                            throw new IndexNotFoundException(aliasAction.index());
                        }
                    }

                    boolean changed = false;
                    MetaData.Builder builder = MetaData.builder(currentState.metaData());
                    for (AliasAction aliasAction : request.actions()) {
                        IndexMetaData indexMetaData = builder.get(aliasAction.index());
                        if (indexMetaData == null) {
                            throw new IndexNotFoundException(aliasAction.index());
                        }
                        // TODO: not copy (putAll)
                        IndexMetaData.Builder indexMetaDataBuilder = IndexMetaData.builder(indexMetaData);
                        if (aliasAction.actionType() == AliasAction.Type.ADD) {
                            String filter = aliasAction.filter();
                            if (Strings.hasLength(filter)) {
                                // parse the filter, in order to validate it
                                IndexService indexService = indices.get(indexMetaData.getIndex());
                                if (indexService == null) {
                                    indexService = indicesService.indexService(indexMetaData.getIndex());
                                    if (indexService == null) {
                                        // temporarily create the index and add mappings so we can parse the filter
                                        try {
                                            indexService = indicesService.createIndex(nodeServicesProvider, indexMetaData, Collections.EMPTY_LIST);
                                            if (indexMetaData.getMappings().containsKey(MapperService.DEFAULT_MAPPING)) {
                                                indexService.mapperService().merge(MapperService.DEFAULT_MAPPING, indexMetaData.getMappings().get(MapperService.DEFAULT_MAPPING).source(), false, false);
                                            }
                                            for (ObjectCursor<MappingMetaData> cursor : indexMetaData.getMappings().values()) {
                                                MappingMetaData mappingMetaData = cursor.value;
                                                indexService.mapperService().merge(mappingMetaData.type(), mappingMetaData.source(), false, false);
                                            }
                                        } catch (Exception e) {
                                            logger.warn("[{}] failed to temporary create in order to apply alias action", e, indexMetaData.getIndex());
                                            continue;
                                        }
                                        indicesToClose.add(indexMetaData.getIndex());
                                    }
                                    indices.put(indexMetaData.getIndex(), indexService);
                                }

                                aliasValidator.validateAliasFilter(aliasAction.alias(), filter, indexService.getQueryShardContext());
                            }
                            AliasMetaData newAliasMd = AliasMetaData.newAliasMetaDataBuilder(
                                    aliasAction.alias())
                                    .filter(filter)
                                    .indexRouting(aliasAction.indexRouting())
                                    .searchRouting(aliasAction.searchRouting())
                                    .build();
                            // Check if this alias already exists
                            AliasMetaData aliasMd = indexMetaData.getAliases().get(aliasAction.alias());
                            if (aliasMd != null && aliasMd.equals(newAliasMd)) {
                                // It's the same alias - ignore it
                                continue;
                            }
                            indexMetaDataBuilder.putAlias(newAliasMd);
                        } else if (aliasAction.actionType() == AliasAction.Type.REMOVE) {
                            if (!indexMetaData.getAliases().containsKey(aliasAction.alias())) {
                                // This alias doesn't exist - ignore
                                continue;
                            }
                            indexMetaDataBuilder.removeAlias(aliasAction.alias());
                        }
                        changed = true;
                        builder.put(indexMetaDataBuilder);
                    }

                    if (changed) {
                        ClusterState updatedState = ClusterState.builder(currentState).metaData(builder).build();
                        // even though changes happened, they resulted in 0 actual changes to metadata
                        // i.e. remove and add the same alias to the same index
                        if (!updatedState.metaData().equalsAliases(currentState.metaData())) {
                            return updatedState;
                        }
                    }
                    return currentState;
                } finally {
                    for (String index : indicesToClose) {
                        indicesService.removeIndex(index, "created for alias processing");
                    }
                }
            }
        });
    }
}
