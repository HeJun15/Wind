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
package org.elasticsearch.search.fetch;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchParseElement;
import org.elasticsearch.search.internal.InternalSearchHit;
import org.elasticsearch.search.internal.SearchContext;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public interface FetchSubPhase {

    public static class HitContext {
        private InternalSearchHit hit;
        private IndexSearcher searcher;
        private LeafReaderContext readerContext;
        private int docId;
        private Map<String, Object> cache;

        public void reset(InternalSearchHit hit, LeafReaderContext context, int docId, IndexSearcher searcher) {
            this.hit = hit;
            this.readerContext = context;
            this.docId = docId;
            this.searcher = searcher;
        }

        public InternalSearchHit hit() {
            return hit;
        }

        public LeafReader reader() {
            return readerContext.reader();
        }

        public LeafReaderContext readerContext() {
            return readerContext;
        }

        public int docId() {
            return docId;
        }

        public IndexReader topLevelReader() {
            return searcher.getIndexReader();
        }

        public IndexSearcher topLevelSearcher() {
            return searcher;
        }

        public Map<String, Object> cache() {
            if (cache == null) {
                cache = new HashMap<>();
            }
            return cache;
        }

        public String getSourcePath(String sourcePath) {
            SearchHit.NestedIdentity nested = hit().getNestedIdentity();
            if (nested != null) {
                // in case of nested we need to figure out what is the _source field from the perspective
                // of the nested hit it self. The nested _source is isolated and the root and potentially parent objects
                // are gone
                StringBuilder nestedPath = new StringBuilder();
                for (; nested != null; nested = nested.getChild()) {
                    nestedPath.append(nested.getField());
                }

                assert sourcePath.startsWith(nestedPath.toString());
                int startIndex = nestedPath.length() + 1; // the path until the deepest nested object + '.'
                return sourcePath.substring(startIndex);
            } else {
                return sourcePath;
            }
        }

    }

    Map<String, ? extends SearchParseElement> parseElements();

    boolean hitExecutionNeeded(SearchContext context);

    /**
     * Executes the hit level phase, with a reader and doc id (note, its a low level reader, and the matching doc).
     */
    void hitExecute(SearchContext context, HitContext hitContext);

    boolean hitsExecutionNeeded(SearchContext context);

    void hitsExecute(SearchContext context, InternalSearchHit[] hits);

    /**
     * This interface is in the fetch phase plugin mechanism.
     * Whenever a new search is executed we create a new {@link SearchContext} that holds individual contexts for each {@link org.elasticsearch.search.fetch.FetchSubPhase}.
     * Fetch phases that use the plugin mechanism must provide a ContextFactory to the SearchContext that creates the fetch phase context and also associates them with a name.
     * See {@link SearchContext#getFetchSubPhaseContext(FetchSubPhase.ContextFactory)}
     */
    public interface ContextFactory<SubPhaseContext extends FetchSubPhaseContext> {

        /**
         * The name of the context.
         */
        public String getName();

        /**
         * Creates a new instance of a FetchSubPhaseContext that holds all information a FetchSubPhase needs to execute on hits.
         */
        public SubPhaseContext newContextInstance();
    }
}
