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

package org.elasticsearch.index.mapper.externalvalues;

import com.spatial4j.core.shape.Point;

import org.apache.lucene.document.Field;
import org.elasticsearch.Version;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.builders.ShapeBuilders;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.mapper.ContentPath;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.index.mapper.MergeResult;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.core.BinaryFieldMapper;
import org.elasticsearch.index.mapper.core.BooleanFieldMapper;
import org.elasticsearch.index.mapper.geo.BaseGeoPointFieldMapper;
import org.elasticsearch.index.mapper.geo.GeoPointFieldMapper;
import org.elasticsearch.index.mapper.geo.GeoPointFieldMapperLegacy;
import org.elasticsearch.index.mapper.geo.GeoShapeFieldMapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.mapper.MapperBuilders.stringField;
import static org.elasticsearch.index.mapper.core.TypeParsers.parseField;
import static org.elasticsearch.index.mapper.core.TypeParsers.parseMultiField;

/**
 * This mapper add a new sub fields
 * .bin Binary type
 * .bool Boolean type
 * .point GeoPoint type
 * .shape GeoShape type
 */
public class ExternalMapper extends FieldMapper {

    public static class Names {
        public static final String FIELD_BIN = "bin";
        public static final String FIELD_BOOL = "bool";
        public static final String FIELD_POINT = "point";
        public static final String FIELD_SHAPE = "shape";
    }

    public static class Builder extends FieldMapper.Builder<Builder, ExternalMapper> {

        private BinaryFieldMapper.Builder binBuilder = new BinaryFieldMapper.Builder(Names.FIELD_BIN);
        private BooleanFieldMapper.Builder boolBuilder = new BooleanFieldMapper.Builder(Names.FIELD_BOOL);
        private GeoPointFieldMapper.Builder pointBuilder = new GeoPointFieldMapper.Builder(Names.FIELD_POINT);
        private GeoPointFieldMapperLegacy.Builder legacyPointBuilder = new GeoPointFieldMapperLegacy.Builder(Names.FIELD_POINT);
        private GeoShapeFieldMapper.Builder shapeBuilder = new GeoShapeFieldMapper.Builder(Names.FIELD_SHAPE);
        private Mapper.Builder stringBuilder;
        private String generatedValue;
        private String mapperName;

        public Builder(String name, String generatedValue, String mapperName) {
            super(name, new ExternalFieldType());
            this.builder = this;
            this.stringBuilder = stringField(name).store(false);
            this.generatedValue = generatedValue;
            this.mapperName = mapperName;
        }

        public Builder string(Mapper.Builder content) {
            this.stringBuilder = content;
            return this;
        }

        @Override
        public ExternalMapper build(BuilderContext context) {
            ContentPath.Type origPathType = context.path().pathType();
            context.path().pathType(ContentPath.Type.FULL);

            context.path().add(name);
            BinaryFieldMapper binMapper = binBuilder.build(context);
            BooleanFieldMapper boolMapper = boolBuilder.build(context);
            BaseGeoPointFieldMapper pointMapper = (context.indexCreatedVersion().before(Version.V_2_2_0)) ?
                    legacyPointBuilder.build(context) : pointBuilder.build(context);
            GeoShapeFieldMapper shapeMapper = shapeBuilder.build(context);
            FieldMapper stringMapper = (FieldMapper)stringBuilder.build(context);
            context.path().remove();

            context.path().pathType(origPathType);
            setupFieldType(context);

            return new ExternalMapper(name, fieldType, generatedValue, mapperName, binMapper, boolMapper, pointMapper, shapeMapper, stringMapper,
                    context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo);
        }
    }

    public static class TypeParser implements Mapper.TypeParser {

        private String generatedValue;
        private String mapperName;

        TypeParser(String mapperName, String generatedValue) {
            this.mapperName = mapperName;
            this.generatedValue = generatedValue;
        }

        @SuppressWarnings({"unchecked"})
        @Override
        public Mapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            ExternalMapper.Builder builder = new ExternalMapper.Builder(name, generatedValue, mapperName);
            parseField(builder, name, node, parserContext);
            for (Iterator<Map.Entry<String, Object>> iterator = node.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<String, Object> entry = iterator.next();
                String propName = Strings.toUnderscoreCase(entry.getKey());
                Object propNode = entry.getValue();

                if (parseMultiField(builder, name, parserContext, propName, propNode)) {
                    iterator.remove();
                }
            }

            return builder;
        }
    }

    static class ExternalFieldType extends MappedFieldType {

        public ExternalFieldType() {}

        protected ExternalFieldType(ExternalFieldType ref) {
            super(ref);
        }

        @Override
        public MappedFieldType clone() {
            return new ExternalFieldType(this);
        }

        @Override
        public String typeName() {
            return "faketype";
        }
    }

    private final String generatedValue;
    private final String mapperName;

    private final BinaryFieldMapper binMapper;
    private final BooleanFieldMapper boolMapper;
    private final BaseGeoPointFieldMapper pointMapper;
    private final GeoShapeFieldMapper shapeMapper;
    private final FieldMapper stringMapper;

    public ExternalMapper(String simpleName, MappedFieldType fieldType,
                          String generatedValue, String mapperName,
                          BinaryFieldMapper binMapper, BooleanFieldMapper boolMapper, BaseGeoPointFieldMapper pointMapper,
                          GeoShapeFieldMapper shapeMapper, FieldMapper stringMapper, Settings indexSettings, MultiFields multiFields, CopyTo copyTo) {
        super(simpleName, fieldType, new ExternalFieldType(), indexSettings, multiFields, copyTo);
        this.generatedValue = generatedValue;
        this.mapperName = mapperName;
        this.binMapper = binMapper;
        this.boolMapper = boolMapper;
        this.pointMapper = pointMapper;
        this.shapeMapper = shapeMapper;
        this.stringMapper = stringMapper;
    }

    @Override
    public Mapper parse(ParseContext context) throws IOException {
        byte[] bytes = "Hello world".getBytes(Charset.defaultCharset());
        binMapper.parse(context.createExternalValueContext(bytes));

        boolMapper.parse(context.createExternalValueContext(true));

        // Let's add a Dummy Point
        Double lat = 42.0;
        Double lng = 51.0;
        GeoPoint point = new GeoPoint(lat, lng);
        pointMapper.parse(context.createExternalValueContext(point));

        // Let's add a Dummy Shape
        Point shape = ShapeBuilders.newPoint(-100, 45).build();
        shapeMapper.parse(context.createExternalValueContext(shape));

        context = context.createExternalValueContext(generatedValue);

        // Let's add a Original String
        stringMapper.parse(context);

        multiFields.parse(this, context);
        return null;
    }

    @Override
    protected void parseCreateField(ParseContext context, List<Field> fields) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void merge(Mapper mergeWith, MergeResult mergeResult) throws MergeMappingException {
        // ignore this for now
    }

    @Override
    public Iterator<Mapper> iterator() {
        return Iterators.concat(super.iterator(), Arrays.asList(binMapper, boolMapper, pointMapper, shapeMapper, stringMapper).iterator());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(simpleName());
        builder.field("type", mapperName);
        multiFields.toXContent(builder, params);
        builder.endObject();
        return builder;
    }

    @Override
    protected String contentType() {
        return mapperName;
    }
}
