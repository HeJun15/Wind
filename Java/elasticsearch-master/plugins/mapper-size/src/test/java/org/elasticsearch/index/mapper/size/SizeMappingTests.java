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

package org.elasticsearch.index.mapper.size;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Collections;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.DocumentMapperParser;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.mapper.SourceToParse;
import org.elasticsearch.indices.mapper.MapperRegistry;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.junit.Before;

public class SizeMappingTests extends ESSingleNodeTestCase {
    
    MapperRegistry mapperRegistry;
    IndexService indexService;
    DocumentMapperParser parser;

    @Before
    public void before() {
        indexService = createIndex("test");
        mapperRegistry = new MapperRegistry(
                Collections.emptyMap(),
                Collections.singletonMap(SizeFieldMapper.NAME, new SizeFieldMapper.TypeParser()));
        parser = new DocumentMapperParser(indexService.getIndexSettings(), indexService.mapperService(),
                indexService.analysisService(), indexService.similarityService(), mapperRegistry);
    }

    public void testSizeEnabled() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_size").field("enabled", true).endObject()
                .endObject().endObject().string();
        DocumentMapper docMapper = parser.parse(mapping);

        BytesReference source = XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .endObject()
                .bytes();
        ParsedDocument doc = docMapper.parse(SourceToParse.source(source).type("type").id("1"));

        assertThat(doc.rootDoc().getField("_size").fieldType().stored(), equalTo(true));
        assertThat(doc.rootDoc().getField("_size").tokenStream(docMapper.mappers().indexAnalyzer(), null), notNullValue());
    }
    
    public void testSizeEnabledAndStoredBackcompat() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_size").field("enabled", true).field("store", "yes").endObject()
                .endObject().endObject().string();
        Settings indexSettings = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_1_4_2.id).build();

        indexService = createIndex("test2", indexSettings);
        mapperRegistry = new MapperRegistry(
                Collections.emptyMap(),
                Collections.singletonMap(SizeFieldMapper.NAME, new SizeFieldMapper.TypeParser()));
        parser = new DocumentMapperParser(indexService.getIndexSettings(), indexService.mapperService(),
                indexService.analysisService(), indexService.similarityService(), mapperRegistry);
        DocumentMapper docMapper = parser.parse(mapping);

        BytesReference source = XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .endObject()
                .bytes();
        ParsedDocument doc = docMapper.parse(SourceToParse.source(source).type("type").id("1"));

        assertThat(doc.rootDoc().getField("_size").fieldType().stored(), equalTo(true));
        assertThat(doc.rootDoc().getField("_size").tokenStream(docMapper.mappers().indexAnalyzer(), null), notNullValue());
    }
    
    public void testSizeDisabled() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_size").field("enabled", false).endObject()
                .endObject().endObject().string();
        DocumentMapper docMapper = parser.parse(mapping);

        BytesReference source = XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .endObject()
                .bytes();
        ParsedDocument doc = docMapper.parse(SourceToParse.source(source).type("type").id("1"));

        assertThat(doc.rootDoc().getField("_size"), nullValue());
    }
    
    public void testSizeNotSet() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .endObject().endObject().string();
        DocumentMapper docMapper = parser.parse(mapping);

        BytesReference source = XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .endObject()
                .bytes();
        ParsedDocument doc = docMapper.parse(SourceToParse.source(source).type("type").id("1"));

        assertThat(doc.rootDoc().getField("_size"), nullValue());
    }
    
    public void testThatDisablingWorksWhenMerging() throws Exception {
        String enabledMapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_size").field("enabled", true).endObject()
                .endObject().endObject().string();
        DocumentMapper enabledMapper = parser.parse(enabledMapping);

        String disabledMapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_size").field("enabled", false).endObject()
                .endObject().endObject().string();
        DocumentMapper disabledMapper = parser.parse(disabledMapping);

        enabledMapper.merge(disabledMapper.mapping(), false, false);
        assertThat(enabledMapper.metadataMapper(SizeFieldMapper.class).enabled(), is(false));
    }
}