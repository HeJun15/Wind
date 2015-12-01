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

package org.elasticsearch.index.cache.bitset;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.join.BitSetProducer;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.common.lucene.index.ElasticsearchDirectoryReader;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.indices.IndicesWarmer;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.IndexSettingsModule;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.equalTo;

public class BitSetFilterCacheTests extends ESTestCase {

    private static final IndexSettings INDEX_SETTINGS = IndexSettingsModule.newIndexSettings(new Index("test"), Settings.EMPTY);
    private final IndicesWarmer warmer = new IndicesWarmer(Settings.EMPTY, null);


    private static int matchCount(BitSetProducer producer, IndexReader reader) throws IOException {
        int count = 0;
        for (LeafReaderContext ctx : reader.leaves()) {
            final BitSet bitSet = producer.getBitSet(ctx);
            if (bitSet != null) {
                count += bitSet.cardinality();
            }
        }
        return count;
    }

    public void testInvalidateEntries() throws Exception {
        IndexWriter writer = new IndexWriter(
                new RAMDirectory(),
                new IndexWriterConfig(new StandardAnalyzer()).setMergePolicy(new LogByteSizeMergePolicy())
        );
        Document document = new Document();
        document.add(new StringField("field", "value", Field.Store.NO));
        writer.addDocument(document);
        writer.commit();

        document = new Document();
        document.add(new StringField("field", "value", Field.Store.NO));
        writer.addDocument(document);
        writer.commit();

        document = new Document();
        document.add(new StringField("field", "value", Field.Store.NO));
        writer.addDocument(document);
        writer.commit();

        IndexReader reader = DirectoryReader.open(writer, false);
        IndexSearcher searcher = new IndexSearcher(reader);

        BitsetFilterCache cache = new BitsetFilterCache(INDEX_SETTINGS, warmer, new BitsetFilterCache.Listener() {
            @Override
            public void onCache(ShardId shardId, Accountable accountable) {

            }

            @Override
            public void onRemoval(ShardId shardId, Accountable accountable) {

            }
        });
        BitSetProducer filter = cache.getBitSetProducer(new TermQuery(new Term("field", "value")));
        assertThat(matchCount(filter, reader), equalTo(3));

        // now cached
        assertThat(matchCount(filter, reader), equalTo(3));
        // There are 3 segments
        assertThat(cache.getLoadedFilters().weight(), equalTo(3L));

        writer.forceMerge(1);
        reader.close();
        reader = DirectoryReader.open(writer, false);
        searcher = new IndexSearcher(reader);

        assertThat(matchCount(filter, reader), equalTo(3));

        // now cached
        assertThat(matchCount(filter, reader), equalTo(3));
        // Only one segment now, so the size must be 1
        assertThat(cache.getLoadedFilters().weight(), equalTo(1L));

        reader.close();
        writer.close();
        // There is no reference from readers and writer to any segment in the test index, so the size in the fbs cache must be 0
        assertThat(cache.getLoadedFilters().weight(), equalTo(0L));
    }

    public void testListener() throws IOException {
        IndexWriter writer = new IndexWriter(
                new RAMDirectory(),
                new IndexWriterConfig(new StandardAnalyzer()).setMergePolicy(new LogByteSizeMergePolicy())
        );
        Document document = new Document();
        document.add(new StringField("field", "value", Field.Store.NO));
        writer.addDocument(document);
        writer.commit();
        final DirectoryReader writerReader = DirectoryReader.open(writer, false);
        final IndexReader reader = randomBoolean() ? writerReader : ElasticsearchDirectoryReader.wrap(writerReader, new ShardId("test", 0));

        final AtomicLong stats = new AtomicLong();
        final AtomicInteger onCacheCalls = new AtomicInteger();
        final AtomicInteger onRemoveCalls = new AtomicInteger();

        final BitsetFilterCache cache = new BitsetFilterCache(INDEX_SETTINGS, warmer, new BitsetFilterCache.Listener() {
            @Override
            public void onCache(ShardId shardId, Accountable accountable) {
                onCacheCalls.incrementAndGet();
                stats.addAndGet(accountable.ramBytesUsed());
                if (writerReader != reader) {
                    assertNotNull(shardId);
                    assertEquals("test", shardId.index().name());
                    assertEquals(0, shardId.id());
                } else {
                    assertNull(shardId);
                }
            }

            @Override
            public void onRemoval(ShardId shardId, Accountable accountable) {
                onRemoveCalls.incrementAndGet();
                stats.addAndGet(-accountable.ramBytesUsed());
                if (writerReader != reader) {
                    assertNotNull(shardId);
                    assertEquals("test", shardId.index().name());
                    assertEquals(0, shardId.id());
                } else {
                    assertNull(shardId);
                }
            }
        });
        BitSetProducer filter = cache.getBitSetProducer(new TermQuery(new Term("field", "value")));
        assertThat(matchCount(filter, reader), equalTo(1));
        assertTrue(stats.get() > 0);
        assertEquals(1, onCacheCalls.get());
        assertEquals(0, onRemoveCalls.get());
        IOUtils.close(reader, writer);
        assertEquals(1, onRemoveCalls.get());
        assertEquals(0, stats.get());
    }

    public void testSetNullListener() {
        try {
            new BitsetFilterCache(INDEX_SETTINGS, warmer, null);
            fail("listener can't be null");
        } catch (IllegalArgumentException ex) {
            assertEquals("listener must not be null", ex.getMessage());
            // all is well
        }
    }

}
