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

package org.elasticsearch.index.query.functionscore;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.GeoUtils;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.LeafScoreFunction;
import org.elasticsearch.common.lucene.search.function.ScoreFunction;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.fielddata.*;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.core.DateFieldMapper;
import org.elasticsearch.index.mapper.core.NumberFieldMapper;
import org.elasticsearch.index.mapper.geo.BaseGeoPointFieldMapper;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.MultiValueMode;

import java.io.IOException;
import java.util.Objects;

public abstract class DecayFunctionBuilder<DFB extends DecayFunctionBuilder> extends ScoreFunctionBuilder<DFB> {

    protected static final String ORIGIN = "origin";
    protected static final String SCALE = "scale";
    protected static final String DECAY = "decay";
    protected static final String OFFSET = "offset";

    public static double DEFAULT_DECAY = 0.5;
    public static MultiValueMode DEFAULT_MULTI_VALUE_MODE = MultiValueMode.MIN;

    private final String fieldName;
    //parsing of origin, scale, offset and decay depends on the field type, delayed to the data node that has the mapping for it
    private final BytesReference functionBytes;
    private MultiValueMode multiValueMode = DEFAULT_MULTI_VALUE_MODE;

    protected DecayFunctionBuilder(String fieldName, Object origin, Object scale, Object offset) {
        this(fieldName, origin, scale, offset, DEFAULT_DECAY);
    }

    protected DecayFunctionBuilder(String fieldName, Object origin, Object scale, Object offset, double decay) {
        if (fieldName == null) {
            throw new IllegalArgumentException("decay function: field name must not be null");
        }
        if (scale == null) {
            throw new IllegalArgumentException("decay function: scale must not be null");
        }
        if (decay <= 0 || decay >= 1.0) {
            throw new IllegalStateException("decay function: decay must be in range 0..1!");
        }
        this.fieldName = fieldName;
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.startObject();
            if (origin != null) {
                builder.field(ORIGIN, origin);
            }
            builder.field(SCALE, scale);
            if (offset != null) {
                builder.field(OFFSET, offset);
            }
            builder.field(DECAY, decay);
            builder.endObject();
            this.functionBytes = builder.bytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("unable to build inner function object",e);
        }
    }

    protected DecayFunctionBuilder(String fieldName, BytesReference functionBytes) {
        if (fieldName == null) {
            throw new IllegalArgumentException("decay function: field name must not be null");
        }
        if (functionBytes == null) {
            throw new IllegalArgumentException("decay function: function must not be null");
        }
        this.fieldName = fieldName;
        this.functionBytes = functionBytes;
    }

    public String getFieldName() {
        return this.fieldName;
    }

    public BytesReference getFunctionBytes() {
        return this.functionBytes;
    }

    @Override
    public void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(getName());
        builder.field(fieldName);
        XContentParser parser = XContentFactory.xContent(functionBytes).createParser(functionBytes);
        builder.copyCurrentStructure(parser);
        builder.field(DecayFunctionParser.MULTI_VALUE_MODE.getPreferredName(), multiValueMode.name());
        builder.endObject();
    }

    public ScoreFunctionBuilder setMultiValueMode(MultiValueMode multiValueMode) {
        if (multiValueMode == null) {
            throw new IllegalArgumentException("decay function: multi_value_mode must not be null");
        }
        this.multiValueMode = multiValueMode;
        return this;
    }

    public MultiValueMode getMultiValueMode() {
        return this.multiValueMode;
    }

    @Override
    protected DFB doReadFrom(StreamInput in) throws IOException {
        DFB decayFunctionBuilder = createFunctionBuilder(in.readString(), in.readBytesReference());
        decayFunctionBuilder.setMultiValueMode(MultiValueMode.readMultiValueModeFrom(in));
        return decayFunctionBuilder;
    }

    protected abstract DFB createFunctionBuilder(String fieldName, BytesReference functionBytes);

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(fieldName);
        out.writeBytesReference(functionBytes);
        multiValueMode.writeTo(out);
    }

    @Override
    protected boolean doEquals(DFB functionBuilder) {
        return Objects.equals(this.fieldName, functionBuilder.getFieldName()) &&
                Objects.equals(this.functionBytes, functionBuilder.getFunctionBytes()) &&
                Objects.equals(this.multiValueMode, functionBuilder.getMultiValueMode());
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(this.fieldName, this.functionBytes, this.multiValueMode);
    }

    @Override
    protected ScoreFunction doToFunction(QueryShardContext context) throws IOException {
        XContentParser parser = XContentFactory.xContent(functionBytes).createParser(functionBytes);
        return parseVariable(fieldName, parser, context, multiValueMode);
    }

    /**
     * Override this function if you want to produce your own scorer.
     * */
    protected abstract DecayFunction getDecayFunction();

    private AbstractDistanceScoreFunction parseVariable(String fieldName, XContentParser parser, QueryShardContext context, MultiValueMode mode) throws IOException {
        //the field must exist, else we cannot read the value for the doc later
        MappedFieldType fieldType = context.fieldMapper(fieldName);
        if (fieldType == null) {
            throw new ParsingException(parser.getTokenLocation(), "unknown field [{}]", fieldName);
        }

        // dates and time need special handling
        parser.nextToken();
        if (fieldType instanceof DateFieldMapper.DateFieldType) {
            return parseDateVariable(parser, context, (DateFieldMapper.DateFieldType) fieldType, mode);
        } else if (fieldType instanceof BaseGeoPointFieldMapper.GeoPointFieldType) {
            return parseGeoVariable(parser, context, (BaseGeoPointFieldMapper.GeoPointFieldType) fieldType, mode);
        } else if (fieldType instanceof NumberFieldMapper.NumberFieldType) {
            return parseNumberVariable(parser, context, (NumberFieldMapper.NumberFieldType) fieldType, mode);
        } else {
            throw new ParsingException(parser.getTokenLocation(), "field [{}] is of type [{}], but only numeric types are supported.", fieldName, fieldType);
        }
    }

    private AbstractDistanceScoreFunction parseNumberVariable(XContentParser parser, QueryShardContext context,
                                                              NumberFieldMapper.NumberFieldType fieldType, MultiValueMode mode) throws IOException {
        XContentParser.Token token;
        String parameterName = null;
        double scale = 0;
        double origin = 0;
        double decay = 0.5;
        double offset = 0.0d;
        boolean scaleFound = false;
        boolean refFound = false;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                parameterName = parser.currentName();
            } else if (DecayFunctionBuilder.SCALE.equals(parameterName)) {
                scale = parser.doubleValue();
                scaleFound = true;
            } else if (DecayFunctionBuilder.DECAY.equals(parameterName)) {
                decay = parser.doubleValue();
            } else if (DecayFunctionBuilder.ORIGIN.equals(parameterName)) {
                origin = parser.doubleValue();
                refFound = true;
            } else if (DecayFunctionBuilder.OFFSET.equals(parameterName)) {
                offset = parser.doubleValue();
            } else {
                throw new ElasticsearchParseException("parameter [{}] not supported!", parameterName);
            }
        }
        if (!scaleFound || !refFound) {
            throw new ElasticsearchParseException("both [{}] and [{}] must be set for numeric fields.", DecayFunctionBuilder.SCALE, DecayFunctionBuilder.ORIGIN);
        }
        IndexNumericFieldData numericFieldData = context.getForField(fieldType);
        return new NumericFieldDataScoreFunction(origin, scale, decay, offset, getDecayFunction(), numericFieldData, mode);
    }

    private AbstractDistanceScoreFunction parseGeoVariable(XContentParser parser, QueryShardContext context,
                                                           BaseGeoPointFieldMapper.GeoPointFieldType fieldType, MultiValueMode mode) throws IOException {
        XContentParser.Token token;
        String parameterName = null;
        GeoPoint origin = new GeoPoint();
        String scaleString = null;
        String offsetString = "0km";
        double decay = 0.5;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                parameterName = parser.currentName();
            } else if (DecayFunctionBuilder.SCALE.equals(parameterName)) {
                scaleString = parser.text();
            } else if (DecayFunctionBuilder.ORIGIN.equals(parameterName)) {
                origin = GeoUtils.parseGeoPoint(parser);
            } else if (DecayFunctionBuilder.DECAY.equals(parameterName)) {
                decay = parser.doubleValue();
            } else if (DecayFunctionBuilder.OFFSET.equals(parameterName)) {
                offsetString = parser.text();
            } else {
                throw new ElasticsearchParseException("parameter [{}] not supported!", parameterName);
            }
        }
        if (origin == null || scaleString == null) {
            throw new ElasticsearchParseException("[{}] and [{}] must be set for geo fields.", DecayFunctionBuilder.ORIGIN, DecayFunctionBuilder.SCALE);
        }
        double scale = DistanceUnit.DEFAULT.parse(scaleString, DistanceUnit.DEFAULT);
        double offset = DistanceUnit.DEFAULT.parse(offsetString, DistanceUnit.DEFAULT);
        IndexGeoPointFieldData indexFieldData = context.getForField(fieldType);
        return new GeoFieldDataScoreFunction(origin, scale, decay, offset, getDecayFunction(), indexFieldData, mode);

    }

    private AbstractDistanceScoreFunction parseDateVariable(XContentParser parser, QueryShardContext context,
                                                            DateFieldMapper.DateFieldType dateFieldType, MultiValueMode mode) throws IOException {
        XContentParser.Token token;
        String parameterName = null;
        String scaleString = null;
        String originString = null;
        String offsetString = "0d";
        double decay = 0.5;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                parameterName = parser.currentName();
            } else if (DecayFunctionBuilder.SCALE.equals(parameterName)) {
                scaleString = parser.text();
            } else if (DecayFunctionBuilder.ORIGIN.equals(parameterName)) {
                originString = parser.text();
            } else if (DecayFunctionBuilder.DECAY.equals(parameterName)) {
                decay = parser.doubleValue();
            } else if (DecayFunctionBuilder.OFFSET.equals(parameterName)) {
                offsetString = parser.text();
            } else {
                throw new ElasticsearchParseException("parameter [{}] not supported!", parameterName);
            }
        }
        long origin;
        if (originString == null) {
            origin = context.nowInMillis();
        } else {
            origin = dateFieldType.parseToMilliseconds(originString, false, null, null);
        }

        if (scaleString == null) {
            throw new ElasticsearchParseException("[{}] must be set for date fields.", DecayFunctionBuilder.SCALE);
        }
        TimeValue val = TimeValue.parseTimeValue(scaleString, TimeValue.timeValueHours(24), DecayFunctionParser.class.getSimpleName() + ".scale");
        double scale = val.getMillis();
        val = TimeValue.parseTimeValue(offsetString, TimeValue.timeValueHours(24), DecayFunctionParser.class.getSimpleName() + ".offset");
        double offset = val.getMillis();
        IndexNumericFieldData numericFieldData = context.getForField(dateFieldType);
        return new NumericFieldDataScoreFunction(origin, scale, decay, offset, getDecayFunction(), numericFieldData, mode);
    }

    static class GeoFieldDataScoreFunction extends AbstractDistanceScoreFunction {

        private final GeoPoint origin;
        private final IndexGeoPointFieldData fieldData;

        private static final GeoDistance distFunction = GeoDistance.DEFAULT;

        public GeoFieldDataScoreFunction(GeoPoint origin, double scale, double decay, double offset, DecayFunction func,
                                         IndexGeoPointFieldData fieldData, MultiValueMode mode) {
            super(scale, decay, offset, func, mode);
            this.origin = origin;
            this.fieldData = fieldData;
        }

        @Override
        public boolean needsScores() {
            return false;
        }

        @Override
        protected NumericDoubleValues distance(LeafReaderContext context) {
            final MultiGeoPointValues geoPointValues = fieldData.load(context).getGeoPointValues();
            return mode.select(new MultiValueMode.UnsortedNumericDoubleValues() {
                @Override
                public int count() {
                    return geoPointValues.count();
                }

                @Override
                public void setDocument(int docId) {
                    geoPointValues.setDocument(docId);
                }

                @Override
                public double valueAt(int index) {
                    GeoPoint other = geoPointValues.valueAt(index);
                    return Math.max(0.0d, distFunction.calculate(origin.lat(), origin.lon(), other.lat(), other.lon(), DistanceUnit.METERS) - offset);
                }
            }, 0.0);
        }

        @Override
        protected String getDistanceString(LeafReaderContext ctx, int docId) {
            StringBuilder values = new StringBuilder(mode.name());
            values.append(" of: [");
            final MultiGeoPointValues geoPointValues = fieldData.load(ctx).getGeoPointValues();
            geoPointValues.setDocument(docId);
            final int num = geoPointValues.count();
            if (num > 0) {
                for (int i = 0; i < num; i++) {
                    GeoPoint value = geoPointValues.valueAt(i);
                    values.append("Math.max(arcDistance(");
                    values.append(value).append("(=doc value),").append(origin).append("(=origin)) - ").append(offset).append("(=offset), 0)");
                    if (i != num - 1) {
                        values.append(", ");
                    }
                }
            } else {
                values.append("0.0");
            }
            values.append("]");
            return values.toString();
        }

        @Override
        protected String getFieldName() {
            return fieldData.getFieldNames().fullName();
        }

        @Override
        protected boolean doEquals(ScoreFunction other) {
            GeoFieldDataScoreFunction geoFieldDataScoreFunction = (GeoFieldDataScoreFunction) other;
            return super.doEquals(other) &&
                    Objects.equals(this.origin, geoFieldDataScoreFunction.origin);
        }
    }

    static class NumericFieldDataScoreFunction extends AbstractDistanceScoreFunction {

        private final IndexNumericFieldData fieldData;
        private final double origin;

        public NumericFieldDataScoreFunction(double origin, double scale, double decay, double offset, DecayFunction func,
                                             IndexNumericFieldData fieldData, MultiValueMode mode) {
            super(scale, decay, offset, func, mode);
            this.fieldData = fieldData;
            this.origin = origin;
        }

        @Override
        public boolean needsScores() {
            return false;
        }

        @Override
        protected NumericDoubleValues distance(LeafReaderContext context) {
            final SortedNumericDoubleValues doubleValues = fieldData.load(context).getDoubleValues();
            return mode.select(new MultiValueMode.UnsortedNumericDoubleValues() {
                @Override
                public int count() {
                    return doubleValues.count();
                }

                @Override
                public void setDocument(int docId) {
                    doubleValues.setDocument(docId);
                }

                @Override
                public double valueAt(int index) {
                    return Math.max(0.0d, Math.abs(doubleValues.valueAt(index) - origin) - offset);
                }
            }, 0.0);
        }

        @Override
        protected String getDistanceString(LeafReaderContext ctx, int docId) {

            StringBuilder values = new StringBuilder(mode.name());
            values.append("[");
            final SortedNumericDoubleValues doubleValues = fieldData.load(ctx).getDoubleValues();
            doubleValues.setDocument(docId);
            final int num = doubleValues.count();
            if (num > 0) {
                for (int i = 0; i < num; i++) {
                    double value = doubleValues.valueAt(i);
                    values.append("Math.max(Math.abs(");
                    values.append(value).append("(=doc value) - ").append(origin).append("(=origin))) - ").append(offset).append("(=offset), 0)");
                    if (i != num - 1) {
                        values.append(", ");
                    }
                }
            } else {
                values.append("0.0");
            }
            values.append("]");
            return values.toString();

        }

        @Override
        protected String getFieldName() {
            return fieldData.getFieldNames().fullName();
        }

        @Override
        protected boolean doEquals(ScoreFunction other) {
            NumericFieldDataScoreFunction numericFieldDataScoreFunction = (NumericFieldDataScoreFunction) other;
            if (super.doEquals(other) == false) {
                return false;
            }
            return Objects.equals(this.origin, numericFieldDataScoreFunction.origin);
        }
    }

    /**
     * This is the base class for scoring a single field.
     *
     * */
    public static abstract class AbstractDistanceScoreFunction extends ScoreFunction {

        private final double scale;
        protected final double offset;
        private final DecayFunction func;
        protected final MultiValueMode mode;

        public AbstractDistanceScoreFunction(double userSuppiedScale, double decay, double offset, DecayFunction func, MultiValueMode mode) {
            super(CombineFunction.MULTIPLY);
            this.mode = mode;
            if (userSuppiedScale <= 0.0) {
                throw new IllegalArgumentException(FunctionScoreQueryBuilder.NAME + " : scale must be > 0.0.");
            }
            if (decay <= 0.0 || decay >= 1.0) {
                throw new IllegalArgumentException(FunctionScoreQueryBuilder.NAME
                        + " : decay must be in the range [0..1].");
            }
            this.scale = func.processScale(userSuppiedScale, decay);
            this.func = func;
            if (offset < 0.0d) {
                throw new IllegalArgumentException(FunctionScoreQueryBuilder.NAME + " : offset must be > 0.0");
            }
            this.offset = offset;
        }

        /**
         * This function computes the distance from a defined origin. Since
         * the value of the document is read from the index, it cannot be
         * guaranteed that the value actually exists. If it does not, we assume
         * the user handles this case in the query and return 0.
         * */
        protected abstract NumericDoubleValues distance(LeafReaderContext context);

        @Override
        public final LeafScoreFunction getLeafScoreFunction(final LeafReaderContext ctx) {
            final NumericDoubleValues distance = distance(ctx);
            return new LeafScoreFunction() {

                @Override
                public double score(int docId, float subQueryScore) {
                    return func.evaluate(distance.get(docId), scale);
                }

                @Override
                public Explanation explainScore(int docId, Explanation subQueryScore) throws IOException {
                    return Explanation.match(
                            CombineFunction.toFloat(score(docId, subQueryScore.getValue())),
                            "Function for field " + getFieldName() + ":",
                            func.explainFunction(getDistanceString(ctx, docId), distance.get(docId), scale));
                }
            };
        }

        protected abstract String getDistanceString(LeafReaderContext ctx, int docId);

        protected abstract String getFieldName();

        @Override
        protected boolean doEquals(ScoreFunction other) {
            AbstractDistanceScoreFunction distanceScoreFunction = (AbstractDistanceScoreFunction) other;
            return Objects.equals(this.scale, distanceScoreFunction.scale) &&
                    Objects.equals(this.offset, distanceScoreFunction.offset) &&
                    Objects.equals(this.mode, distanceScoreFunction.mode) &&
                    Objects.equals(this.func, distanceScoreFunction.func) &&
                    Objects.equals(this.getFieldName(), distanceScoreFunction.getFieldName());
        }
    }
}
