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
package org.elasticsearch.common;

import org.elasticsearch.test.ESTestCase;

import java.util.HashSet;
import java.util.Set;

public class UUIDTests extends ESTestCase {

    static UUIDGenerator timeUUIDGen = new TimeBasedUUIDGenerator();
    static UUIDGenerator randomUUIDGen = new RandomBasedUUIDGenerator();

    public void testRandomUUID() {
        verifyUUIDSet(100000, randomUUIDGen);
    }

    public void testTimeUUID() {
        verifyUUIDSet(100000, timeUUIDGen);
    }

    public void testThreadedTimeUUID() {
        testUUIDThreaded(timeUUIDGen);
    }

    public void testThreadedRandomUUID() {
        testUUIDThreaded(randomUUIDGen);
    }

    Set<String> verifyUUIDSet(int count, UUIDGenerator uuidSource) {
        HashSet<String> uuidSet = new HashSet<>();
        for (int i = 0; i < count; ++i) {
            uuidSet.add(uuidSource.getBase64UUID());
        }
        assertEquals(count, uuidSet.size());
        return uuidSet;
    }

    class UUIDGenRunner implements Runnable {
        int count;
        public Set<String> uuidSet = null;
        UUIDGenerator uuidSource;

        public UUIDGenRunner(int count, UUIDGenerator uuidSource) {
            this.count = count;
            this.uuidSource = uuidSource;
        }

        @Override
        public void run() {
            uuidSet = verifyUUIDSet(count, uuidSource);
        }
    }

    public void testUUIDThreaded(UUIDGenerator uuidSource) {
        HashSet<UUIDGenRunner> runners = new HashSet<>();
        HashSet<Thread> threads = new HashSet<>();
        int count = 20;
        int uuids = 10000;
        for (int i = 0; i < count; ++i) {
            UUIDGenRunner runner = new UUIDGenRunner(uuids, uuidSource);
            Thread t = new Thread(runner);
            threads.add(t);
            runners.add(runner);
        }
        for (Thread t : threads) {
            t.start();
        }
        boolean retry = false;
        do {
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException ie) {
                    retry = true;
                }
            }
        } while (retry);

        HashSet<String> globalSet = new HashSet<>();
        for (UUIDGenRunner runner : runners) {
            globalSet.addAll(runner.uuidSet);
        }
        assertEquals(count*uuids, globalSet.size());
    }
}
