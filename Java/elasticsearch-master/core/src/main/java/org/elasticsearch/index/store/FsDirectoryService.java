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

package org.elasticsearch.index.store;

import org.apache.lucene.store.*;
import org.apache.lucene.util.Constants;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.metrics.CounterMetric;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.shard.ShardPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 */
public class FsDirectoryService extends DirectoryService implements StoreRateLimiting.Listener, StoreRateLimiting.Provider {

    protected final IndexStore indexStore;

    private final CounterMetric rateLimitingTimeInNanos = new CounterMetric();
    private final ShardPath path;

    @Inject
    public FsDirectoryService(IndexSettings indexSettings, IndexStore indexStore, ShardPath path) {
        super(path.getShardId(), indexSettings);
        this.path = path;
        this.indexStore = indexStore;
    }

    @Override
    public long throttleTimeInNanos() {
        return rateLimitingTimeInNanos.count();
    }

    @Override
    public StoreRateLimiting rateLimiting() {
        return indexStore.rateLimiting();
    }

    public static LockFactory buildLockFactory(IndexSettings indexSettings) {
        final Settings settings = indexSettings.getSettings();
        String fsLock = settings.get("index.store.fs.lock", settings.get("index.store.fs.fs_lock", "native"));
        LockFactory lockFactory;
        if (fsLock.equals("native")) {
            lockFactory = NativeFSLockFactory.INSTANCE;
        } else if (fsLock.equals("simple")) {
            lockFactory = SimpleFSLockFactory.INSTANCE;
        } else {
            throw new IllegalArgumentException("unrecognized fs_lock \"" + fsLock + "\": must be native or simple");
        }
        return lockFactory;
    }

    protected final LockFactory buildLockFactory() throws IOException {
        return buildLockFactory(indexSettings);
    }

    @Override
    public Directory newDirectory() throws IOException {
        final Path location = path.resolveIndex();
        Files.createDirectories(location);
        Directory wrapped = newFSDirectory(location, buildLockFactory());
        return new RateLimitedFSDirectory(wrapped, this, this) ;
    }


    @Override
    public void onPause(long nanos) {
        rateLimitingTimeInNanos.inc(nanos);
    }

    /*
    * We are mmapping norms, docvalues as well as term dictionaries, all other files are served through NIOFS
    * this provides good random access performance while not creating unnecessary mmaps for files like stored
    * fields etc.
    */
    private static final Set<String> PRIMARY_EXTENSIONS = Collections.unmodifiableSet(Sets.newHashSet("nvd", "dvd", "tim"));


    protected Directory newFSDirectory(Path location, LockFactory lockFactory) throws IOException {
        final String storeType = indexSettings.getSettings().get(IndexModule.STORE_TYPE, IndexModule.Type.DEFAULT.getSettingsKey());
        if (IndexModule.Type.FS.match(storeType) || IndexModule.Type.DEFAULT.match(storeType)) {
            final FSDirectory open = FSDirectory.open(location, lockFactory); // use lucene defaults
            if (open instanceof MMapDirectory && Constants.WINDOWS == false) {
                return newDefaultDir(location, (MMapDirectory) open, lockFactory);
            }
            return open;
        } else if (IndexModule.Type.SIMPLEFS.match(storeType)) {
            return new SimpleFSDirectory(location, lockFactory);
        } else if (IndexModule.Type.NIOFS.match(storeType)) {
            return new NIOFSDirectory(location, lockFactory);
        } else if (IndexModule.Type.MMAPFS.match(storeType)) {
            return new MMapDirectory(location, lockFactory);
        }
        throw new IllegalArgumentException("No directory found for type [" + storeType + "]");
    }

    private Directory newDefaultDir(Path location, final MMapDirectory mmapDir, LockFactory lockFactory) throws IOException {
        return new FileSwitchDirectory(PRIMARY_EXTENSIONS, mmapDir, new NIOFSDirectory(location, lockFactory), true) {
            @Override
            public String[] listAll() throws IOException {
                // Avoid doing listAll twice:
                return mmapDir.listAll();
            }
        };
    }
}
