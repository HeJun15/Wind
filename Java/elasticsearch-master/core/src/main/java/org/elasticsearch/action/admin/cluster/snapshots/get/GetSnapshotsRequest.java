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

package org.elasticsearch.action.admin.cluster.snapshots.get;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.master.MasterNodeRequest;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;

import static org.elasticsearch.action.ValidateActions.addValidationError;

/**
 * Get snapshot request
 */
public class GetSnapshotsRequest extends MasterNodeRequest<GetSnapshotsRequest> {

    public static final String ALL_SNAPSHOTS = "_all";
    public static final String CURRENT_SNAPSHOT = "_current";

    private String repository;

    private String[] snapshots = Strings.EMPTY_ARRAY;

    private boolean ignoreUnavailable;

    public GetSnapshotsRequest() {
    }

    /**
     * Constructs a new get snapshots request with given repository name and list of snapshots
     *
     * @param repository repository name
     * @param snapshots  list of snapshots
     */
    public GetSnapshotsRequest(String repository, String[] snapshots) {
        this.repository = repository;
        this.snapshots = snapshots;
    }

    /**
     * Constructs a new get snapshots request with given repository name
     *
     * @param repository repository name
     */
    public GetSnapshotsRequest(String repository) {
        this.repository = repository;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (repository == null) {
            validationException = addValidationError("repository is missing", validationException);
        }
        return validationException;
    }

    /**
     * Sets repository name
     *
     * @param repository repository name
     * @return this request
     */
    public GetSnapshotsRequest repository(String repository) {
        this.repository = repository;
        return this;
    }

    /**
     * Returns repository name
     *
     * @return repository name
     */
    public String repository() {
        return this.repository;
    }

    /**
     * Returns the names of the snapshots.
     *
     * @return the names of snapshots
     */
    public String[] snapshots() {
        return this.snapshots;
    }

    /**
     * Sets the list of snapshots to be returned
     *
     * @return this request
     */
    public GetSnapshotsRequest snapshots(String[] snapshots) {
        this.snapshots = snapshots;
        return this;
    }

    /**
     * Set to true to ignore unavailable snapshots
     *
     * @return this request
     */
    public GetSnapshotsRequest ignoreUnavailable(boolean ignoreUnavailable) {
        this.ignoreUnavailable = ignoreUnavailable;
        return this;
    }
    /**
     * @return Whether snapshots should be ignored when unavailable (corrupt or temporarily not fetchable)
     */
    public boolean ignoreUnavailable() {
        return ignoreUnavailable;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        repository = in.readString();
        snapshots = in.readStringArray();
        ignoreUnavailable = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(repository);
        out.writeStringArray(snapshots);
        out.writeBoolean(ignoreUnavailable);
    }
}
