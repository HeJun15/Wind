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

package org.elasticsearch.index.query;

import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.support.QueryParsers;
import org.elasticsearch.index.search.MatchQuery;
import org.elasticsearch.index.search.MultiMatchQuery;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Same as {@link MatchQueryBuilder} but supports multiple fields.
 */
public class MultiMatchQueryBuilder extends AbstractQueryBuilder<MultiMatchQueryBuilder> {

    public static final String NAME = "multi_match";

    public static final MultiMatchQueryBuilder.Type DEFAULT_TYPE = MultiMatchQueryBuilder.Type.BEST_FIELDS;
    public static final Operator DEFAULT_OPERATOR = Operator.OR;
    public static final int DEFAULT_PHRASE_SLOP = MatchQuery.DEFAULT_PHRASE_SLOP;
    public static final int DEFAULT_PREFIX_LENGTH = FuzzyQuery.defaultPrefixLength;
    public static final int DEFAULT_MAX_EXPANSIONS = FuzzyQuery.defaultMaxExpansions;
    public static final boolean DEFAULT_LENIENCY = MatchQuery.DEFAULT_LENIENCY;
    public static final MatchQuery.ZeroTermsQuery DEFAULT_ZERO_TERMS_QUERY = MatchQuery.DEFAULT_ZERO_TERMS_QUERY;

    private final Object value;
    private final Map<String, Float> fieldsBoosts;
    private MultiMatchQueryBuilder.Type type = DEFAULT_TYPE;
    private Operator operator = DEFAULT_OPERATOR;
    private String analyzer;
    private int slop = DEFAULT_PHRASE_SLOP;
    private Fuzziness fuzziness;
    private int prefixLength = DEFAULT_PREFIX_LENGTH;
    private int maxExpansions = DEFAULT_MAX_EXPANSIONS;
    private String minimumShouldMatch;
    private String fuzzyRewrite = null;
    private Boolean useDisMax;
    private Float tieBreaker;
    private boolean lenient = DEFAULT_LENIENCY;
    private Float cutoffFrequency = null;
    private MatchQuery.ZeroTermsQuery zeroTermsQuery = DEFAULT_ZERO_TERMS_QUERY;

    static final MultiMatchQueryBuilder PROTOTYPE = new MultiMatchQueryBuilder("");

    public enum Type implements Writeable<Type> {

        /**
         * Uses the best matching boolean field as main score and uses
         * a tie-breaker to adjust the score based on remaining field matches
         */
        BEST_FIELDS(MatchQuery.Type.BOOLEAN, 0.0f, new ParseField("best_fields", "boolean")),

        /**
         * Uses the sum of the matching boolean fields to score the query
         */
        MOST_FIELDS(MatchQuery.Type.BOOLEAN, 1.0f, new ParseField("most_fields")),

        /**
         * Uses a blended DocumentFrequency to dynamically combine the queried
         * fields into a single field given the configured analysis is identical.
         * This type uses a tie-breaker to adjust the score based on remaining
         * matches per analyzed terms
         */
        CROSS_FIELDS(MatchQuery.Type.BOOLEAN, 0.0f, new ParseField("cross_fields")),

        /**
         * Uses the best matching phrase field as main score and uses
         * a tie-breaker to adjust the score based on remaining field matches
         */
        PHRASE(MatchQuery.Type.PHRASE, 0.0f, new ParseField("phrase")),

        /**
         * Uses the best matching phrase-prefix field as main score and uses
         * a tie-breaker to adjust the score based on remaining field matches
         */
        PHRASE_PREFIX(MatchQuery.Type.PHRASE_PREFIX, 0.0f, new ParseField("phrase_prefix"));

        private static final Type PROTOTYPE = BEST_FIELDS;

        private MatchQuery.Type matchQueryType;
        private final float tieBreaker;
        private final ParseField parseField;

        Type (MatchQuery.Type matchQueryType, float tieBreaker, ParseField parseField) {
            this.matchQueryType = matchQueryType;
            this.tieBreaker = tieBreaker;
            this.parseField = parseField;
        }

        public float tieBreaker() {
            return this.tieBreaker;
        }

        public MatchQuery.Type matchQueryType() {
            return matchQueryType;
        }

        public ParseField parseField() {
            return parseField;
        }

        public static Type parse(String value, ParseFieldMatcher parseFieldMatcher) {
            MultiMatchQueryBuilder.Type[] values = MultiMatchQueryBuilder.Type.values();
            Type type = null;
            for (MultiMatchQueryBuilder.Type t : values) {
                if (parseFieldMatcher.match(value, t.parseField())) {
                    type = t;
                    break;
                }
            }
            if (type == null) {
                throw new ElasticsearchParseException("failed to parse [{}] query type [{}]. unknown type.", NAME, value);
            }
            return type;
        }

        @Override
        public Type readFrom(StreamInput in) throws IOException {
            return Type.values()[in.readVInt()];
        }

        public static Type readTypeFrom(StreamInput in) throws IOException {
            return PROTOTYPE.readFrom(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(this.ordinal());
        }
    }

    /**
     * Returns the type (for testing)
     */
    public MultiMatchQueryBuilder.Type getType() {
        return type;
    }

    /**
     * Constructs a new text query.
     */
    public MultiMatchQueryBuilder(Object value, String... fields) {
        if (value == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires query value");
        }
        if (fields == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires fields at initalization time");
        }
        this.value = value;
        this.fieldsBoosts = new TreeMap<>();
        for (String field : fields) {
            field(field);
        }
    }

    public Object value() {
        return value;
    }

    /**
     * Adds a field to run the multi match against.
     */
    public MultiMatchQueryBuilder field(String field) {
        if (Strings.isEmpty(field)) {
            throw new IllegalArgumentException("supplied field is null or empty.");
        }
        this.fieldsBoosts.put(field, AbstractQueryBuilder.DEFAULT_BOOST);
        return this;
    }

    /**
     * Adds a field to run the multi match against with a specific boost.
     */
    public MultiMatchQueryBuilder field(String field, float boost) {
        if (Strings.isEmpty(field)) {
            throw new IllegalArgumentException("supplied field is null or empty.");
        }
        this.fieldsBoosts.put(field, boost);
        return this;
    }

    /**
     * Add several fields to run the query against with a specific boost.
     */
    public MultiMatchQueryBuilder fields(Map<String, Float> fields) {
        this.fieldsBoosts.putAll(fields);
        return this;
    }

    public Map<String, Float> fields() {
        return fieldsBoosts;
    }

    /**
     * Sets the type of the text query.
     */
    public MultiMatchQueryBuilder type(MultiMatchQueryBuilder.Type type) {
        if (type == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires type to be non-null");
        }
        this.type = type;
        return this;
    }

    /**
     * Sets the type of the text query.
     */
    public MultiMatchQueryBuilder type(Object type) {
        if (type == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires type to be non-null");
        }
        this.type = Type.parse(type.toString().toLowerCase(Locale.ROOT), ParseFieldMatcher.EMPTY);
        return this;
    }

    public Type type() {
        return type;
    }

    /**
     * Sets the operator to use when using a boolean query. Defaults to <tt>OR</tt>.
     */
    public MultiMatchQueryBuilder operator(Operator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires operator to be non-null");
        }
        this.operator = operator;
        return this;
    }

    public Operator operator() {
        return operator;
    }

    /**
     * Explicitly set the analyzer to use. Defaults to use explicit mapping config for the field, or, if not
     * set, the default search analyzer.
     */
    public MultiMatchQueryBuilder analyzer(String analyzer) {
        this.analyzer = analyzer;
        return this;
    }

    public String analyzer() {
        return analyzer;
    }

    /**
     * Set the phrase slop if evaluated to a phrase query type.
     */
    public MultiMatchQueryBuilder slop(int slop) {
        if (slop < 0) {
            throw new IllegalArgumentException("No negative slop allowed.");
        }
        this.slop = slop;
        return this;
    }

    public int slop() {
        return slop;
    }

    /**
     * Sets the fuzziness used when evaluated to a fuzzy query type. Defaults to "AUTO".
     */
    public MultiMatchQueryBuilder fuzziness(Object fuzziness) {
        if (fuzziness != null) {
            this.fuzziness = Fuzziness.build(fuzziness);
        }
        return this;
    }

    public Fuzziness fuzziness() {
        return fuzziness;
    }

    public MultiMatchQueryBuilder prefixLength(int prefixLength) {
        if (prefixLength < 0) {
            throw new IllegalArgumentException("No negative prefix length allowed.");
        }
        this.prefixLength = prefixLength;
        return this;
    }

    public int prefixLength() {
        return prefixLength;
    }

    /**
     * When using fuzzy or prefix type query, the number of term expansions to use. Defaults to unbounded
     * so its recommended to set it to a reasonable value for faster execution.
     */
    public MultiMatchQueryBuilder maxExpansions(int maxExpansions) {
        if (maxExpansions <= 0) {
            throw new IllegalArgumentException("Max expansions must be strictly great than zero.");
        }
        this.maxExpansions = maxExpansions;
        return this;
    }

    public int maxExpansions() {
        return maxExpansions;
    }

    public MultiMatchQueryBuilder minimumShouldMatch(String minimumShouldMatch) {
        this.minimumShouldMatch = minimumShouldMatch;
        return this;
    }

    public String minimumShouldMatch() {
        return minimumShouldMatch;
    }

    public MultiMatchQueryBuilder fuzzyRewrite(String fuzzyRewrite) {
        this.fuzzyRewrite = fuzzyRewrite;
        return this;
    }

    public String fuzzyRewrite() {
        return fuzzyRewrite;
    }

    /**
     * @deprecated use a tieBreaker of 1.0f to disable "dis-max"
     * query or select the appropriate {@link Type}
     */
    @Deprecated
    public MultiMatchQueryBuilder useDisMax(Boolean useDisMax) {
        this.useDisMax = useDisMax;
        return this;
    }

    public Boolean useDisMax() {
        return useDisMax;
    }

    /**
     * <p>Tie-Breaker for "best-match" disjunction queries (OR-Queries).
     * The tie breaker capability allows documents that match more than one query clause
     * (in this case on more than one field) to be scored better than documents that
     * match only the best of the fields, without confusing this with the better case of
     * two distinct matches in the multiple fields.</p>
     *
     * <p>A tie-breaker value of <tt>1.0</tt> is interpreted as a signal to score queries as
     * "most-match" queries where all matching query clauses are considered for scoring.</p>
     *
     * @see Type
     */
    public MultiMatchQueryBuilder tieBreaker(float tieBreaker) {
        this.tieBreaker = tieBreaker;
        return this;
    }

    /**
     * <p>Tie-Breaker for "best-match" disjunction queries (OR-Queries).
     * The tie breaker capability allows documents that match more than one query clause
     * (in this case on more than one field) to be scored better than documents that
     * match only the best of the fields, without confusing this with the better case of
     * two distinct matches in the multiple fields.</p>
     *
     * <p>A tie-breaker value of <tt>1.0</tt> is interpreted as a signal to score queries as
     * "most-match" queries where all matching query clauses are considered for scoring.</p>
     *
     * @see Type
     */
    public MultiMatchQueryBuilder tieBreaker(Float tieBreaker) {
        this.tieBreaker = tieBreaker;
        return this;
    }

    public Float tieBreaker() {
        return tieBreaker;
    }

    /**
     * Sets whether format based failures will be ignored.
     */
    public MultiMatchQueryBuilder lenient(boolean lenient) {
        this.lenient = lenient;
        return this;
    }

    public boolean lenient() {
        return lenient;
    }

    /**
     * Set a cutoff value in [0..1] (or absolute number &gt;=1) representing the
     * maximum threshold of a terms document frequency to be considered a low
     * frequency term.
     */
    public MultiMatchQueryBuilder cutoffFrequency(float cutoff) {
        this.cutoffFrequency = cutoff;
        return this;
    }

    /**
     * Set a cutoff value in [0..1] (or absolute number &gt;=1) representing the
     * maximum threshold of a terms document frequency to be considered a low
     * frequency term.
     */
    public MultiMatchQueryBuilder cutoffFrequency(Float cutoff) {
        this.cutoffFrequency = cutoff;
        return this;
    }

    public Float cutoffFrequency() {
        return cutoffFrequency;
    }

    public MultiMatchQueryBuilder zeroTermsQuery(MatchQuery.ZeroTermsQuery zeroTermsQuery) {
        if (zeroTermsQuery == null) {
            throw new IllegalArgumentException("[" + NAME + "] requires zero terms query to be non-null");
        }
        this.zeroTermsQuery = zeroTermsQuery;
        return this;
    }

    public MatchQuery.ZeroTermsQuery zeroTermsQuery() {
        return zeroTermsQuery;
    }

    @Override
    public void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(MultiMatchQueryParser.QUERY_FIELD.getPreferredName(), value);
        builder.startArray(MultiMatchQueryParser.FIELDS_FIELD.getPreferredName());
        for (Map.Entry<String, Float> fieldEntry : this.fieldsBoosts.entrySet()) {
            builder.value(fieldEntry.getKey() + "^" + fieldEntry.getValue());
        }
        builder.endArray();
        builder.field(MultiMatchQueryParser.TYPE_FIELD.getPreferredName(), type.toString().toLowerCase(Locale.ENGLISH));
        builder.field(MultiMatchQueryParser.OPERATOR_FIELD.getPreferredName(), operator.toString());
        if (analyzer != null) {
            builder.field(MultiMatchQueryParser.ANALYZER_FIELD.getPreferredName(), analyzer);
        }
        builder.field(MultiMatchQueryParser.SLOP_FIELD.getPreferredName(), slop);
        if (fuzziness != null) {
            fuzziness.toXContent(builder, params);
        }
        builder.field(MultiMatchQueryParser.PREFIX_LENGTH_FIELD.getPreferredName(), prefixLength);
        builder.field(MultiMatchQueryParser.MAX_EXPANSIONS_FIELD.getPreferredName(), maxExpansions);
        if (minimumShouldMatch != null) {
            builder.field(MultiMatchQueryParser.MINIMUM_SHOULD_MATCH_FIELD.getPreferredName(), minimumShouldMatch);
        }
        if (fuzzyRewrite != null) {
            builder.field(MultiMatchQueryParser.FUZZY_REWRITE_FIELD.getPreferredName(), fuzzyRewrite);
        }
        if (useDisMax != null) {
            builder.field(MultiMatchQueryParser.USE_DIS_MAX_FIELD.getPreferredName(), useDisMax);
        }
        if (tieBreaker != null) {
            builder.field(MultiMatchQueryParser.TIE_BREAKER_FIELD.getPreferredName(), tieBreaker);
        }
        builder.field(MultiMatchQueryParser.LENIENT_FIELD.getPreferredName(), lenient);
        if (cutoffFrequency != null) {
            builder.field(MultiMatchQueryParser.CUTOFF_FREQUENCY_FIELD.getPreferredName(), cutoffFrequency);
        }
        builder.field(MultiMatchQueryParser.ZERO_TERMS_QUERY_FIELD.getPreferredName(), zeroTermsQuery.toString());
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        MultiMatchQuery multiMatchQuery = new MultiMatchQuery(context);
        if (analyzer != null) {
            if (context.getAnalysisService().analyzer(analyzer) == null) {
                throw new QueryShardException(context, "[" + NAME + "] analyzer [" + analyzer + "] not found");
            }
            multiMatchQuery.setAnalyzer(analyzer);
        }
        multiMatchQuery.setPhraseSlop(slop);
        if (fuzziness != null) {
            multiMatchQuery.setFuzziness(fuzziness);
        }
        multiMatchQuery.setFuzzyPrefixLength(prefixLength);
        multiMatchQuery.setMaxExpansions(maxExpansions);
        multiMatchQuery.setOccur(operator.toBooleanClauseOccur());
        if (fuzzyRewrite != null) {
            multiMatchQuery.setFuzzyRewriteMethod(QueryParsers.parseRewriteMethod(context.parseFieldMatcher(), fuzzyRewrite, null));
        }
        if (tieBreaker != null) {
            multiMatchQuery.setTieBreaker(tieBreaker);
        }
        if (cutoffFrequency != null) {
            multiMatchQuery.setCommonTermsCutoff(cutoffFrequency);
        }
        multiMatchQuery.setLenient(lenient);
        multiMatchQuery.setZeroTermsQuery(zeroTermsQuery);

        if (useDisMax != null) { // backwards foobar
            boolean typeUsesDismax = type.tieBreaker() != 1.0f;
            if (typeUsesDismax != useDisMax) {
                if (useDisMax && tieBreaker == null) {
                    multiMatchQuery.setTieBreaker(0.0f);
                } else {
                    multiMatchQuery.setTieBreaker(1.0f);
                }
            }
        }

        Map<String, Float> newFieldsBoosts = handleFieldsMatchPattern(context.getMapperService(), fieldsBoosts);

        Query query = multiMatchQuery.parse(type, newFieldsBoosts, value, minimumShouldMatch);
        if (query == null) {
            return null;
        }
        return query;
    }

    private static Map<String, Float> handleFieldsMatchPattern(MapperService mapperService, Map<String, Float> fieldsBoosts) {
        Map<String, Float> newFieldsBoosts = new TreeMap<>();
        for (Map.Entry<String, Float> fieldBoost : fieldsBoosts.entrySet()) {
            String fField = fieldBoost.getKey();
            Float fBoost = fieldBoost.getValue();
            if (Regex.isSimpleMatchPattern(fField)) {
                for (String field : mapperService.simpleMatchToIndexNames(fField)) {
                    newFieldsBoosts.put(field, fBoost);
                }
            } else {
                newFieldsBoosts.put(fField, fBoost);
            }
        }
        return newFieldsBoosts;
    }

    @Override
    protected MultiMatchQueryBuilder doReadFrom(StreamInput in) throws IOException {
        MultiMatchQueryBuilder multiMatchQuery = new MultiMatchQueryBuilder(in.readGenericValue());
        int size = in.readVInt();
        for (int i = 0; i < size; i++) {
            multiMatchQuery.fieldsBoosts.put(in.readString(), in.readFloat());
        }
        multiMatchQuery.type = MultiMatchQueryBuilder.Type.readTypeFrom(in);
        multiMatchQuery.operator = Operator.readOperatorFrom(in);
        multiMatchQuery.analyzer = in.readOptionalString();
        multiMatchQuery.slop = in.readVInt();
        if (in.readBoolean()) {
            multiMatchQuery.fuzziness = Fuzziness.readFuzzinessFrom(in);
        }
        multiMatchQuery.prefixLength = in.readVInt();
        multiMatchQuery.maxExpansions = in.readVInt();
        multiMatchQuery.minimumShouldMatch = in.readOptionalString();
        multiMatchQuery.fuzzyRewrite = in.readOptionalString();
        multiMatchQuery.useDisMax = in.readOptionalBoolean();
        multiMatchQuery.tieBreaker = (Float) in.readGenericValue();
        multiMatchQuery.lenient = in.readBoolean();
        multiMatchQuery.cutoffFrequency = (Float) in.readGenericValue();
        multiMatchQuery.zeroTermsQuery = MatchQuery.ZeroTermsQuery.readZeroTermsQueryFrom(in);
        return multiMatchQuery;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeGenericValue(value);
        out.writeVInt(fieldsBoosts.size());
        for (Map.Entry<String, Float> fieldsEntry : fieldsBoosts.entrySet()) {
            out.writeString(fieldsEntry.getKey());
            out.writeFloat(fieldsEntry.getValue());
        }
        type.writeTo(out);
        operator.writeTo(out);
        out.writeOptionalString(analyzer);
        out.writeVInt(slop);
        if (fuzziness != null) {
            out.writeBoolean(true);
            fuzziness.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeVInt(prefixLength);
        out.writeVInt(maxExpansions);
        out.writeOptionalString(minimumShouldMatch);
        out.writeOptionalString(fuzzyRewrite);
        out.writeOptionalBoolean(useDisMax);
        out.writeGenericValue(tieBreaker);
        out.writeBoolean(lenient);
        out.writeGenericValue(cutoffFrequency);
        zeroTermsQuery.writeTo(out);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(value, fieldsBoosts, type, operator, analyzer, slop, fuzziness,
                prefixLength, maxExpansions, minimumShouldMatch, fuzzyRewrite, useDisMax, tieBreaker, lenient,
                cutoffFrequency, zeroTermsQuery);
    }

    @Override
    protected boolean doEquals(MultiMatchQueryBuilder other) {
        return Objects.equals(value, other.value) &&
                Objects.equals(fieldsBoosts, other.fieldsBoosts) &&
                Objects.equals(type, other.type) &&
                Objects.equals(operator, other.operator) &&
                Objects.equals(analyzer, other.analyzer) &&
                Objects.equals(slop, other.slop) &&
                Objects.equals(fuzziness, other.fuzziness) &&
                Objects.equals(prefixLength, other.prefixLength) &&
                Objects.equals(maxExpansions, other.maxExpansions) &&
                Objects.equals(minimumShouldMatch, other.minimumShouldMatch) &&
                Objects.equals(fuzzyRewrite, other.fuzzyRewrite) &&
                Objects.equals(useDisMax, other.useDisMax) &&
                Objects.equals(tieBreaker, other.tieBreaker) &&
                Objects.equals(lenient, other.lenient) &&
                Objects.equals(cutoffFrequency, other.cutoffFrequency) &&
                Objects.equals(zeroTermsQuery, other.zeroTermsQuery);
    }
}
