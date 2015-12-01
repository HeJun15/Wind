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

package org.elasticsearch.common.util.concurrent;

import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.index.engine.EngineException;

import java.util.concurrent.locks.Lock;

/**
 * Releasable lock used inside of Engine implementations
 */
public class ReleasableLock implements Releasable {
    private final Lock lock;

    /* a per thread boolean indicating the lock is held by it. only works when assertions are enabled */
    private final ThreadLocal<Boolean> holdingThreads;

    public ReleasableLock(Lock lock) {
        this.lock = lock;
        boolean useHoldingThreads = false;
        assert (useHoldingThreads = true);
        if (useHoldingThreads) {
            holdingThreads = new ThreadLocal<>();
        } else {
            holdingThreads = null;
        }
    }

    @Override
    public void close() {
        lock.unlock();
        assert removeCurrentThread();
    }


    public ReleasableLock acquire() throws EngineException {
        lock.lock();
        assert addCurrentThread();
        return this;
    }

    private boolean addCurrentThread() {
        holdingThreads.set(true);
        return true;
    }

    private boolean removeCurrentThread() {
        holdingThreads.remove();
        return true;
    }

    public Boolean isHeldByCurrentThread() {
        if (holdingThreads == null) {
            throw new UnsupportedOperationException("asserts must be enabled");
        }
        Boolean b = holdingThreads.get();
        return b != null && b.booleanValue();
    }
}
