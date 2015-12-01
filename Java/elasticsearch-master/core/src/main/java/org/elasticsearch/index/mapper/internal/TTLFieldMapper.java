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

package org.elasticsearch.index.mapper.internal;

import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexOptions;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.AlreadyExpiredException;
import org.elasticsearch.index.analysis.NumericLongAnalyzer;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.index.mapper.MergeResult;
import org.elasticsearch.index.mapper.MetadataFieldMapper;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.SourceToParse;
import org.elasticsearch.index.mapper.core.LongFieldMapper;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.xcontent.support.XContentMapValues.nodeBooleanValue;
import static org.elasticsearch.common.xcontent.support.XContentMapValues.nodeTimeValue;

public class TTLFieldMapper extends MetadataFieldMapper {

    public static final String NAME = "_ttl";
    public static final String CONTENT_TYPE = "_ttl";

    public static class Defaults extends LongFieldMapper.Defaults {
        public static final String NAME = TTLFieldMapper.CONTENT_TYPE;

        public static final TTLFieldType TTL_FIELD_TYPE = new TTLFieldType();

        static {
            TTL_FIELD_TYPE.setIndexOptions(IndexOptions.DOCS);
            TTL_FIELD_TYPE.setStored(true);
            TTL_FIELD_TYPE.setTokenized(false);
            TTL_FIELD_TYPE.setNumericPrecisionStep(Defaults.PRECISION_STEP_64_BIT);
            TTL_FIELD_TYPE.setIndexAnalyzer(NumericLongAnalyzer.buildNamedAnalyzer(Defaults.PRECISION_STEP_64_BIT));
            TTL_FIELD_TYPE.setSearchAnalyzer(NumericLongAnalyzer.buildNamedAnalyzer(Integer.MAX_VALUE));
            TTL_FIELD_TYPE.setNames(new MappedFieldType.Names(NAME));
            TTL_FIELD_TYPE.freeze();
        }

        public static final EnabledAttributeMapper ENABLED_STATE = EnabledAttributeMapper.UNSET_DISABLED;
        public static final long DEFAULT = -1;
    }

    public static class Builder extends MetadataFieldMapper.Builder<Builder, TTLFieldMapper> {

        private EnabledAttributeMapper enabledState = EnabledAttributeMapper.UNSET_DISABLED;
        private long defaultTTL = Defaults.DEFAULT;

        public Builder() {
            super(Defaults.NAME, Defaults.TTL_FIELD_TYPE);
        }

        public Builder enabled(EnabledAttributeMapper enabled) {
            this.enabledState = enabled;
            return builder;
        }

        public Builder defaultTTL(long defaultTTL) {
            this.defaultTTL = defaultTTL;
            return builder;
        }

        @Override
        public TTLFieldMapper build(BuilderContext context) {
            setupFieldType(context);
            fieldType.setHasDocValues(false);
            return new TTLFieldMapper(fieldType, enabledState, defaultTTL, fieldDataSettings, context.indexSettings());
        }
    }

    public static class TypeParser implements MetadataFieldMapper.TypeParser {
        @Override
        public MetadataFieldMapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            Builder builder = new Builder();
            for (Iterator<Map.Entry<String, Object>> iterator = node.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<String, Object> entry = iterator.next();
                String fieldName = Strings.toUnderscoreCase(entry.getKey());
                Object fieldNode = entry.getValue();
                if (fieldName.equals("enabled")) {
                    EnabledAttributeMapper enabledState = nodeBooleanValue(fieldNode) ? EnabledAttributeMapper.ENABLED : EnabledAttributeMapper.DISABLED;
                    builder.enabled(enabledState);
                    iterator.remove();
                } else if (fieldName.equals("default")) {
                    TimeValue ttlTimeValue = nodeTimeValue(fieldNode, null);
                    if (ttlTimeValue != null) {
                        builder.defaultTTL(ttlTimeValue.millis());
                    }
                    iterator.remove();
                }
            }
            return builder;
        }

        @Override
        public MetadataFieldMapper getDefault(Settings indexSettings, MappedFieldType fieldType, String typeName) {
            return new TTLFieldMapper(indexSettings);
        }
    }

    public static final class TTLFieldType extends LongFieldMapper.LongFieldType {

        public TTLFieldType() {
        }

        protected TTLFieldType(TTLFieldType ref) {
            super(ref);
        }

        @Override
        public TTLFieldType clone() {
            return new TTLFieldType(this);
        }

        // Overrides valueForSearch to display live value of remaining ttl
        @Override
        public Object valueForSearch(Object value) {
            long now;
            SearchContext searchContext = SearchContext.current();
            if (searchContext != null) {
                now = searchContext.nowInMillis();
            } else {
                now = System.currentTimeMillis();
            }
            long val = value(value);
            return val - now;
        }
    }

    private EnabledAttributeMapper enabledState;
    private long defaultTTL;

    private TTLFieldMapper(Settings indexSettings) {
        this(Defaults.TTL_FIELD_TYPE.clone(), Defaults.ENABLED_STATE, Defaults.DEFAULT, null, indexSettings);
    }

    private TTLFieldMapper(MappedFieldType fieldType, EnabledAttributeMapper enabled, long defaultTTL,
                             @Nullable Settings fieldDataSettings, Settings indexSettings) {
        super(NAME, fieldType, Defaults.TTL_FIELD_TYPE, indexSettings);
        this.enabledState = enabled;
        this.defaultTTL = defaultTTL;
    }

    public boolean enabled() {
        return this.enabledState.enabled;
    }

    public long defaultTTL() {
        return this.defaultTTL;
    }

    // Other implementation for realtime get display
    public Object valueForSearch(long expirationTime) {
        return expirationTime - System.currentTimeMillis();
    }

    @Override
    public void preParse(ParseContext context) throws IOException {
    }

    @Override
    public void postParse(ParseContext context) throws IOException {
        super.parse(context);
    }

    @Override
    public Mapper parse(ParseContext context) throws IOException, MapperParsingException {
        if (context.sourceToParse().ttl() < 0) { // no ttl has been provided externally
            long ttl;
            if (context.parser().currentToken() == XContentParser.Token.VALUE_STRING) {
                ttl = TimeValue.parseTimeValue(context.parser().text(), null, "ttl").millis();
            } else {
                ttl = context.parser().longValue(true);
            }
            if (ttl <= 0) {
                throw new MapperParsingException("TTL value must be > 0. Illegal value provided [" + ttl + "]");
            }
            context.sourceToParse().ttl(ttl);
        }
        return null;
    }

    @Override
    protected void parseCreateField(ParseContext context, List<Field> fields) throws IOException, AlreadyExpiredException {
        if (enabledState.enabled && !context.sourceToParse().flyweight()) {
            long ttl = context.sourceToParse().ttl();
            if (ttl <= 0 && defaultTTL > 0) { // no ttl provided so we use the default value
                ttl = defaultTTL;
                context.sourceToParse().ttl(ttl);
            }
            if (ttl > 0) { // a ttl has been provided either externally or in the _source
                long timestamp = context.sourceToParse().timestamp();
                long expire = new Date(timestamp + ttl).getTime();
                long now = System.currentTimeMillis();
                // there is not point indexing already expired doc
                if (context.sourceToParse().origin() == SourceToParse.Origin.PRIMARY && now >= expire) {
                    throw new AlreadyExpiredException(context.index(), context.type(), context.id(), timestamp, ttl, now);
                }
                // the expiration timestamp (timestamp + ttl) is set as field
                fields.add(new LongFieldMapper.CustomLongNumericField(expire, fieldType()));
            }
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        boolean includeDefaults = params.paramAsBoolean("include_defaults", false);

        // if all are defaults, no sense to write it at all
        if (!includeDefaults && enabledState == Defaults.ENABLED_STATE && defaultTTL == Defaults.DEFAULT) {
            return builder;
        }
        builder.startObject(CONTENT_TYPE);
        if (includeDefaults || enabledState != Defaults.ENABLED_STATE) {
            builder.field("enabled", enabledState.enabled);
        }
        if (includeDefaults || defaultTTL != Defaults.DEFAULT && enabledState.enabled) {
            builder.field("default", defaultTTL);
        }
        builder.endObject();
        return builder;
    }

    @Override
    protected String contentType() {
        return NAME;
    }

    @Override
    public void merge(Mapper mergeWith, MergeResult mergeResult) throws MergeMappingException {
        TTLFieldMapper ttlMergeWith = (TTLFieldMapper) mergeWith;
        if (((TTLFieldMapper) mergeWith).enabledState != Defaults.ENABLED_STATE) {//only do something if actually something was set for the document mapper that we merge with
            if (this.enabledState == EnabledAttributeMapper.ENABLED && ((TTLFieldMapper) mergeWith).enabledState == EnabledAttributeMapper.DISABLED) {
                mergeResult.addConflict("_ttl cannot be disabled once it was enabled.");
            } else {
                if (!mergeResult.simulate()) {
                    this.enabledState = ttlMergeWith.enabledState;
                }
            }
        }
        if (ttlMergeWith.defaultTTL != -1) {
            // we never build the default when the field is disabled so we should also not set it
            // (it does not make a difference though as everything that is not build in toXContent will also not be set in the cluster)
            if (!mergeResult.simulate() && (enabledState == EnabledAttributeMapper.ENABLED)) {
                this.defaultTTL = ttlMergeWith.defaultTTL;
            }
        }
    }
}
