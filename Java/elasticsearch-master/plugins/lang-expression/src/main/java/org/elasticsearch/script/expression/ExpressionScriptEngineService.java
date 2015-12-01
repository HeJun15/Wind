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

package org.elasticsearch.script.expression;

import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.expressions.js.VariableContext;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.apache.lucene.search.SortField;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.core.DateFieldMapper;
import org.elasticsearch.index.mapper.core.NumberFieldMapper;
import org.elasticsearch.script.CompiledScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptEngineService;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.MultiValueMode;
import org.elasticsearch.search.lookup.SearchLookup;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Map;

/**
 * Provides the infrastructure for Lucene expressions as a scripting language for Elasticsearch.  Only
 * {@link SearchScript}s are supported.
 */
public class ExpressionScriptEngineService extends AbstractComponent implements ScriptEngineService {

    public static final String NAME = "expression";

    protected static final String GET_YEAR_METHOD         = "getYear";
    protected static final String GET_MONTH_METHOD        = "getMonth";
    protected static final String GET_DAY_OF_MONTH_METHOD = "getDayOfMonth";
    protected static final String GET_HOUR_OF_DAY_METHOD  = "getHourOfDay";
    protected static final String GET_MINUTES_METHOD      = "getMinutes";
    protected static final String GET_SECONDS_METHOD      = "getSeconds";

    protected static final String MINIMUM_METHOD          = "min";
    protected static final String MAXIMUM_METHOD          = "max";
    protected static final String AVERAGE_METHOD          = "avg";
    protected static final String MEDIAN_METHOD           = "median";
    protected static final String SUM_METHOD              = "sum";
    protected static final String COUNT_METHOD            = "count";

    @Inject
    public ExpressionScriptEngineService(Settings settings) {
        super(settings);
    }

    @Override
    public String[] types() {
        return new String[]{NAME};
    }

    @Override
    public String[] extensions() {
        return new String[]{NAME};
    }

    @Override
    public boolean sandboxed() {
        return true;
    }

    @Override
    public Object compile(String script) {
        // classloader created here
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SpecialPermission());
        }
        return AccessController.doPrivileged(new PrivilegedAction<Expression>() {
            @Override
            public Expression run() {
                try {
                    // NOTE: validation is delayed to allow runtime vars, and we don't have access to per index stuff here
                    return JavascriptCompiler.compile(script);
                } catch (ParseException e) {
                    throw new ScriptException("Failed to parse expression: " + script, e);
                }
            }
        });
    }

    @Override
    public SearchScript search(CompiledScript compiledScript, SearchLookup lookup, @Nullable Map<String, Object> vars) {
        try {
            Expression expr = (Expression)compiledScript.compiled();
            MapperService mapper = lookup.doc().mapperService();
            // NOTE: if we need to do anything complicated with bindings in the future, we can just extend Bindings,
            // instead of complicating SimpleBindings (which should stay simple)
            SimpleBindings bindings = new SimpleBindings();
            ReplaceableConstValueSource specialValue = null;

            for (String variable : expr.variables) {
                if (variable.equals("_score")) {
                    bindings.add(new SortField("_score", SortField.Type.SCORE));
                } else if (variable.equals("_value")) {
                    specialValue = new ReplaceableConstValueSource();
                    bindings.add("_value", specialValue);
                    // noop: _value is special for aggregations, and is handled in ExpressionScriptBindings
                    // TODO: if some uses it in a scoring expression, they will get a nasty failure when evaluating...need a
                    // way to know this is for aggregations and so _value is ok to have...

                } else if (vars != null && vars.containsKey(variable)) {
                    // TODO: document and/or error if vars contains _score?
                    // NOTE: by checking for the variable in vars first, it allows masking document fields with a global constant,
                    // but if we were to reverse it, we could provide a way to supply dynamic defaults for documents missing the field?
                    Object value = vars.get(variable);
                    if (value instanceof Number) {
                        bindings.add(variable, new DoubleConstValueSource(((Number) value).doubleValue()));
                    } else {
                        throw new ScriptException("Parameter [" + variable + "] must be a numeric type");
                    }

                } else {
                    String fieldname = null;
                    String methodname = null;
                    VariableContext[] parts = VariableContext.parse(variable);
                    if (parts[0].text.equals("doc") == false) {
                        throw new ScriptException("Unknown variable [" + parts[0].text + "] in expression");
                    }
                    if (parts.length < 2 || parts[1].type != VariableContext.Type.STR_INDEX) {
                        throw new ScriptException("Variable 'doc' in expression must be used with a specific field like: doc['myfield']");
                    } else {
                        fieldname = parts[1].text;
                    }
                    if (parts.length == 3) {
                        if (parts[2].type == VariableContext.Type.METHOD) {
                            methodname = parts[2].text;
                        } else if (parts[2].type != VariableContext.Type.MEMBER || !"value".equals(parts[2].text)) {
                            throw new ScriptException("Only the member variable [value] or member methods may be accessed on a field when not accessing the field directly");
                        }
                    }
                    if (parts.length > 3) {
                        throw new ScriptException("Variable [" + variable + "] does not follow an allowed format of either doc['field'] or doc['field'].method()");
                    }

                    MappedFieldType fieldType = mapper.smartNameFieldType(fieldname);

                    if (fieldType == null) {
                        throw new ScriptException("Field [" + fieldname + "] used in expression does not exist in mappings");
                    }
                    if (fieldType.isNumeric() == false) {
                        // TODO: more context (which expression?)
                        throw new ScriptException("Field [" + fieldname + "] used in expression must be numeric");
                    }

                    IndexFieldData<?> fieldData = lookup.doc().fieldDataService().getForField((NumberFieldMapper.NumberFieldType) fieldType);
                    if (methodname == null) {
                        bindings.add(variable, new FieldDataValueSource(fieldData, MultiValueMode.MIN));
                    } else {
                        bindings.add(variable, getMethodValueSource(fieldType, fieldData, fieldname, methodname));
                    }
                }
            }

            final boolean needsScores = expr.getSortField(bindings, false).needsScores();
            return new ExpressionSearchScript(compiledScript, bindings, specialValue, needsScores);
        } catch (Exception exception) {
            throw new ScriptException("Error during search with " + compiledScript, exception);
        }
    }

    protected ValueSource getMethodValueSource(MappedFieldType fieldType, IndexFieldData<?> fieldData, String fieldName, String methodName) {
        switch (methodName) {
            case GET_YEAR_METHOD:
                return getDateMethodValueSource(fieldType, fieldData, fieldName, methodName, Calendar.YEAR);
            case GET_MONTH_METHOD:
                return getDateMethodValueSource(fieldType, fieldData, fieldName, methodName, Calendar.MONTH);
            case GET_DAY_OF_MONTH_METHOD:
                return getDateMethodValueSource(fieldType, fieldData, fieldName, methodName, Calendar.DAY_OF_MONTH);
            case GET_HOUR_OF_DAY_METHOD:
                return getDateMethodValueSource(fieldType, fieldData, fieldName, methodName, Calendar.HOUR_OF_DAY);
            case GET_MINUTES_METHOD:
                return getDateMethodValueSource(fieldType, fieldData, fieldName, methodName, Calendar.MINUTE);
            case GET_SECONDS_METHOD:
                return getDateMethodValueSource(fieldType, fieldData, fieldName, methodName, Calendar.SECOND);
            case MINIMUM_METHOD:
                return new FieldDataValueSource(fieldData, MultiValueMode.MIN);
            case MAXIMUM_METHOD:
                return new FieldDataValueSource(fieldData, MultiValueMode.MAX);
            case AVERAGE_METHOD:
                return new FieldDataValueSource(fieldData, MultiValueMode.AVG);
            case MEDIAN_METHOD:
                return new FieldDataValueSource(fieldData, MultiValueMode.MEDIAN);
            case SUM_METHOD:
                return new FieldDataValueSource(fieldData, MultiValueMode.SUM);
            case COUNT_METHOD:
                return new CountMethodValueSource(fieldData);
            default:
                throw new IllegalArgumentException("Member method [" + methodName + "] does not exist.");
        }
    }

    protected ValueSource getDateMethodValueSource(MappedFieldType fieldType, IndexFieldData<?> fieldData, String fieldName, String methodName, int calendarType) {
        if (!(fieldType instanceof DateFieldMapper.DateFieldType)) {
            throw new IllegalArgumentException("Member method [" + methodName + "] can only be used with a date field type, not the field [" + fieldName + "].");
        }

        return new DateMethodValueSource(fieldData, MultiValueMode.MIN, methodName, calendarType);
    }

    @Override
    public ExecutableScript executable(CompiledScript compiledScript, Map<String, Object> vars) {
        return new ExpressionExecutableScript(compiledScript, vars);
    }

    @Override
    public void close() {}

    @Override
    public void scriptRemoved(CompiledScript script) {
        // Nothing to do
    }
}
