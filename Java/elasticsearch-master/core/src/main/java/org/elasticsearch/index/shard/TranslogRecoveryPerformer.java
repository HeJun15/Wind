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
package org.elasticsearch.index.shard;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.IgnoreOnRecoveryEngineException;
import org.elasticsearch.index.mapper.*;
import org.elasticsearch.index.translog.Translog;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.index.mapper.SourceToParse.source;

/**
 * The TranslogRecoveryPerformer encapsulates all the logic needed to transform a translog entry into an
 * indexing operation including source parsing and field creation from the source.
 */
public class TranslogRecoveryPerformer {
    private final MapperService mapperService;
    private final ESLogger logger;
    private final Map<String, Mapping> recoveredTypes = new HashMap<>();
    private final ShardId shardId;

    protected TranslogRecoveryPerformer(ShardId shardId, MapperService mapperService, ESLogger logger) {
        this.shardId = shardId;
        this.mapperService = mapperService;
        this.logger = logger;
    }

    protected DocumentMapperForType docMapper(String type) {
        return mapperService.documentMapperWithAutoCreate(type); // protected for testing
    }

    /**
     * Applies all operations in the iterable to the current engine and returns the number of operations applied.
     * This operation will stop applying operations once an operation failed to apply.
     *
     * Throws a {@link MapperException} to be thrown if a mapping update is encountered.
     */
    int performBatchRecovery(Engine engine, Iterable<Translog.Operation> operations) {
        int numOps = 0;
        try {
            for (Translog.Operation operation : operations) {
                performRecoveryOperation(engine, operation, false);
                numOps++;
            }
        } catch (Throwable t) {
            throw new BatchOperationException(shardId, "failed to apply batch translog operation", numOps, t);
        }
        return numOps;
    }

    public static class BatchOperationException extends ElasticsearchException {

        private final int completedOperations;

        public BatchOperationException(ShardId shardId, String msg, int completedOperations, Throwable cause) {
            super(msg, cause);
            setShard(shardId);
            this.completedOperations = completedOperations;
        }

        public BatchOperationException(StreamInput in) throws IOException{
            super(in);
            completedOperations = in.readInt();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeInt(completedOperations);
        }

        /** the number of succesful operations performed before the exception was thrown */
        public int completedOperations() {
            return completedOperations;
        }
    }

    private void maybeAddMappingUpdate(String type, Mapping update, String docId, boolean allowMappingUpdates) {
        if (update == null) {
            return;
        }
        if (allowMappingUpdates == false) {
            throw new MapperException("mapping updates are not allowed (type: [" + type + "], id: [" + docId + "])");
        }
        Mapping currentUpdate = recoveredTypes.get(type);
        if (currentUpdate == null) {
            recoveredTypes.put(type, update);
        } else {
            MapperUtils.merge(currentUpdate, update);
        }
    }

    /**
     * Performs a single recovery operation.
     *
     * @param allowMappingUpdates true if mapping update should be accepted (but collected). Setting it to false will
     *                            cause a {@link MapperException} to be thrown if an update
     *                            is encountered.
     */
    public void performRecoveryOperation(Engine engine, Translog.Operation operation, boolean allowMappingUpdates) {
        try {
            switch (operation.opType()) {
                case INDEX:
                    Translog.Index index = (Translog.Index) operation;
                    Engine.Index engineIndex = IndexShard.prepareIndex(docMapper(index.type()), source(index.source()).type(index.type()).id(index.id())
                                    .routing(index.routing()).parent(index.parent()).timestamp(index.timestamp()).ttl(index.ttl()),
                            index.version(), index.versionType().versionTypeForReplicationAndRecovery(), Engine.Operation.Origin.RECOVERY);
                    maybeAddMappingUpdate(engineIndex.type(), engineIndex.parsedDoc().dynamicMappingsUpdate(), engineIndex.id(), allowMappingUpdates);
                    if (logger.isTraceEnabled()) {
                        logger.trace("[translog] recover [index] op of [{}][{}]", index.type(), index.id());
                    }
                    engine.index(engineIndex);
                    break;
                case DELETE:
                    Translog.Delete delete = (Translog.Delete) operation;
                    Uid uid = Uid.createUid(delete.uid().text());
                    if (logger.isTraceEnabled()) {
                        logger.trace("[translog] recover [delete] op of [{}][{}]", uid.type(), uid.id());
                    }
                    engine.delete(new Engine.Delete(uid.type(), uid.id(), delete.uid(), delete.version(),
                            delete.versionType().versionTypeForReplicationAndRecovery(), Engine.Operation.Origin.RECOVERY, System.nanoTime(), false));
                    break;
                default:
                    throw new IllegalStateException("No operation defined for [" + operation + "]");
            }
        } catch (ElasticsearchException e) {
            boolean hasIgnoreOnRecoveryException = false;
            ElasticsearchException current = e;
            while (true) {
                if (current instanceof IgnoreOnRecoveryEngineException) {
                    hasIgnoreOnRecoveryException = true;
                    break;
                }
                if (current.getCause() instanceof ElasticsearchException) {
                    current = (ElasticsearchException) current.getCause();
                } else {
                    break;
                }
            }
            if (!hasIgnoreOnRecoveryException) {
                throw e;
            }
        }
        operationProcessed();
    }


    /**
     * Called once for every processed operation by this recovery performer.
     * This can be used to get progress information on the translog execution.
     */
    protected void operationProcessed() {
        // noop
    }

    /**
     * Returns the recovered types modifying the mapping during the recovery
     */
    public Map<String, Mapping> getRecoveredTypes() {
        return recoveredTypes;
    }
}
