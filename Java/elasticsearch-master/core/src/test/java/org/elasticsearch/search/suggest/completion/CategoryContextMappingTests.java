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

package org.elasticsearch.search.suggest.completion;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.suggest.document.ContextSuggestField;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.index.mapper.*;
import org.elasticsearch.search.suggest.completion.context.CategoryContextMapping;
import org.elasticsearch.search.suggest.completion.context.CategoryQueryContext;
import org.elasticsearch.search.suggest.completion.context.ContextBuilder;
import org.elasticsearch.search.suggest.completion.context.ContextMapping;
import org.elasticsearch.test.ESSingleNodeTestCase;

import java.util.*;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isIn;

public class CategoryContextMappingTests extends ESSingleNodeTestCase {

    public void testIndexingWithNoContexts() throws Exception {
        String mapping = jsonBuilder().startObject().startObject("type1")
                .startObject("properties").startObject("completion")
                .field("type", "completion")
                .startArray("contexts")
                .startObject()
                .field("name", "ctx")
                .field("type", "category")
                .endObject()
                .endArray()
                .endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping);
        FieldMapper fieldMapper = defaultMapper.mappers().getMapper("completion");
        MappedFieldType completionFieldType = fieldMapper.fieldType();
        ParsedDocument parsedDocument = defaultMapper.parse("test", "type1", "1", jsonBuilder()
                .startObject()
                .startArray("completion")
                .startObject()
                .array("input", "suggestion1", "suggestion2")
                .field("weight", 3)
                .endObject()
                .startObject()
                .array("input", "suggestion3", "suggestion4")
                .field("weight", 4)
                .endObject()
                .startObject()
                .field("input", "suggestion5", "suggestion6", "suggestion7")
                .field("weight", 5)
                .endObject()
                .endArray()
                .endObject()
                .bytes());
        IndexableField[] fields = parsedDocument.rootDoc().getFields(completionFieldType.names().indexName());
        assertContextSuggestFields(fields, 7);
    }

    public void testIndexingWithSimpleContexts() throws Exception {
        String mapping = jsonBuilder().startObject().startObject("type1")
                .startObject("properties").startObject("completion")
                .field("type", "completion")
                .startArray("contexts")
                .startObject()
                .field("name", "ctx")
                .field("type", "category")
                .endObject()
                .endArray()
                .endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping);
        FieldMapper fieldMapper = defaultMapper.mappers().getMapper("completion");
        MappedFieldType completionFieldType = fieldMapper.fieldType();
        ParsedDocument parsedDocument = defaultMapper.parse("test", "type1", "1", jsonBuilder()
                .startObject()
                .startArray("completion")
                .startObject()
                .field("input", "suggestion5", "suggestion6", "suggestion7")
                .startObject("contexts")
                .field("ctx", "ctx1")
                .endObject()
                .field("weight", 5)
                .endObject()
                .endArray()
                .endObject()
                .bytes());
        IndexableField[] fields = parsedDocument.rootDoc().getFields(completionFieldType.names().indexName());
        assertContextSuggestFields(fields, 3);
    }

    public void testIndexingWithContextList() throws Exception {
        String mapping = jsonBuilder().startObject().startObject("type1")
                .startObject("properties").startObject("completion")
                .field("type", "completion")
                .startArray("contexts")
                .startObject()
                .field("name", "ctx")
                .field("type", "category")
                .endObject()
                .endArray()
                .endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping);
        FieldMapper fieldMapper = defaultMapper.mappers().getMapper("completion");
        MappedFieldType completionFieldType = fieldMapper.fieldType();
        ParsedDocument parsedDocument = defaultMapper.parse("test", "type1", "1", jsonBuilder()
                .startObject()
                .startObject("completion")
                .field("input", "suggestion5", "suggestion6", "suggestion7")
                .startObject("contexts")
                .array("ctx", "ctx1", "ctx2", "ctx3")
                .endObject()
                .field("weight", 5)
                .endObject()
                .endObject()
                .bytes());
        IndexableField[] fields = parsedDocument.rootDoc().getFields(completionFieldType.names().indexName());
        assertContextSuggestFields(fields, 3);
    }

    public void testIndexingWithMultipleContexts() throws Exception {
        String mapping = jsonBuilder().startObject().startObject("type1")
                .startObject("properties").startObject("completion")
                .field("type", "completion")
                .startArray("contexts")
                .startObject()
                .field("name", "ctx")
                .field("type", "category")
                .endObject()
                .startObject()
                .field("name", "type")
                .field("type", "category")
                .endObject()
                .endArray()
                .endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping);
        FieldMapper fieldMapper = defaultMapper.mappers().getMapper("completion");
        MappedFieldType completionFieldType = fieldMapper.fieldType();
        XContentBuilder builder = jsonBuilder()
                .startObject()
                .startArray("completion")
                .startObject()
                .field("input", "suggestion5", "suggestion6", "suggestion7")
                .field("weight", 5)
                .startObject("contexts")
                .array("ctx", "ctx1", "ctx2", "ctx3")
                .array("type", "typr3", "ftg")
                .endObject()
                .endObject()
                .endArray()
                .endObject();
        ParsedDocument parsedDocument = defaultMapper.parse("test", "type1", "1", builder.bytes());
        IndexableField[] fields = parsedDocument.rootDoc().getFields(completionFieldType.names().indexName());
        assertContextSuggestFields(fields, 3);
    }

    public void testQueryContextParsingBasic() throws Exception {
        XContentBuilder builder = jsonBuilder().value("context1");
        XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.bytes());
        CategoryContextMapping mapping = ContextBuilder.category("cat").build();
        List<ContextMapping.QueryContext> queryContexts = mapping.parseQueryContext(parser);
        assertThat(queryContexts.size(), equalTo(1));
        assertThat(queryContexts.get(0).context, equalTo("context1"));
        assertThat(queryContexts.get(0).boost, equalTo(1));
        assertThat(queryContexts.get(0).isPrefix, equalTo(false));
    }

    public void testQueryContextParsingArray() throws Exception {
        XContentBuilder builder = jsonBuilder().startArray()
                .value("context1")
                .value("context2")
                .endArray();
        XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.bytes());
        CategoryContextMapping mapping = ContextBuilder.category("cat").build();
        List<ContextMapping.QueryContext> queryContexts = mapping.parseQueryContext(parser);
        assertThat(queryContexts.size(), equalTo(2));
        assertThat(queryContexts.get(0).context, equalTo("context1"));
        assertThat(queryContexts.get(0).boost, equalTo(1));
        assertThat(queryContexts.get(0).isPrefix, equalTo(false));
        assertThat(queryContexts.get(1).context, equalTo("context2"));
        assertThat(queryContexts.get(1).boost, equalTo(1));
        assertThat(queryContexts.get(1).isPrefix, equalTo(false));
    }

    public void testQueryContextParsingObject() throws Exception {
        XContentBuilder builder = jsonBuilder().startObject()
                .field("context", "context1")
                .field("boost", 10)
                .field("prefix", true)
                .endObject();
        XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.bytes());
        CategoryContextMapping mapping = ContextBuilder.category("cat").build();
        List<ContextMapping.QueryContext> queryContexts = mapping.parseQueryContext(parser);
        assertThat(queryContexts.size(), equalTo(1));
        assertThat(queryContexts.get(0).context, equalTo("context1"));
        assertThat(queryContexts.get(0).boost, equalTo(10));
        assertThat(queryContexts.get(0).isPrefix, equalTo(true));
    }


    public void testQueryContextParsingObjectArray() throws Exception {
        XContentBuilder builder = jsonBuilder().startArray()
                .startObject()
                .field("context", "context1")
                .field("boost", 2)
                .field("prefix", true)
                .endObject()
                .startObject()
                .field("context", "context2")
                .field("boost", 3)
                .field("prefix", false)
                .endObject()
                .endArray();
        XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.bytes());
        CategoryContextMapping mapping = ContextBuilder.category("cat").build();
        List<ContextMapping.QueryContext> queryContexts = mapping.parseQueryContext(parser);
        assertThat(queryContexts.size(), equalTo(2));
        assertThat(queryContexts.get(0).context, equalTo("context1"));
        assertThat(queryContexts.get(0).boost, equalTo(2));
        assertThat(queryContexts.get(0).isPrefix, equalTo(true));
        assertThat(queryContexts.get(1).context, equalTo("context2"));
        assertThat(queryContexts.get(1).boost, equalTo(3));
        assertThat(queryContexts.get(1).isPrefix, equalTo(false));
    }

    public void testQueryContextParsingMixed() throws Exception {
        XContentBuilder builder = jsonBuilder().startArray()
                .startObject()
                .field("context", "context1")
                .field("boost", 2)
                .field("prefix", true)
                .endObject()
                .value("context2")
                .endArray();
        XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.bytes());
        CategoryContextMapping mapping = ContextBuilder.category("cat").build();
        List<ContextMapping.QueryContext> queryContexts = mapping.parseQueryContext(parser);
        assertThat(queryContexts.size(), equalTo(2));
        assertThat(queryContexts.get(0).context, equalTo("context1"));
        assertThat(queryContexts.get(0).boost, equalTo(2));
        assertThat(queryContexts.get(0).isPrefix, equalTo(true));
        assertThat(queryContexts.get(1).context, equalTo("context2"));
        assertThat(queryContexts.get(1).boost, equalTo(1));
        assertThat(queryContexts.get(1).isPrefix, equalTo(false));
    }

    public void testParsingContextFromDocument() throws Exception {
        CategoryContextMapping mapping = ContextBuilder.category("cat").field("category").build();
        ParseContext.Document document = new ParseContext.Document();
        document.add(new StringField("category", "category1", Field.Store.NO));
        Set<CharSequence> context = mapping.parseContext(document);
        assertThat(context.size(), equalTo(1));
        assertTrue(context.contains("category1"));
    }

    static void assertContextSuggestFields(IndexableField[] fields, int expected) {
        int actualFieldCount = 0;
        for (IndexableField field : fields) {
            if (field instanceof ContextSuggestField) {
                actualFieldCount++;
            }
        }
        assertThat(actualFieldCount, equalTo(expected));
    }
}
