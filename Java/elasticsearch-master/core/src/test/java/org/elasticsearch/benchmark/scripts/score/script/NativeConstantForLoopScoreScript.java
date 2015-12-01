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

package org.elasticsearch.benchmark.scripts.score.script;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.script.AbstractSearchScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.NativeScriptFactory;

import java.util.Map;

public class NativeConstantForLoopScoreScript extends AbstractSearchScript {

    public static final String NATIVE_CONSTANT_FOR_LOOP_SCRIPT_SCORE = "native_constant_for_loop_script_score";
    
    public static class Factory implements NativeScriptFactory {

        @Override
        public ExecutableScript newScript(@Nullable Map<String, Object> params) {
            return new NativeConstantForLoopScoreScript(params);
        }

        @Override
        public boolean needsScores() {
            return false;
        }
    }

    private NativeConstantForLoopScoreScript(Map<String, Object> params) {

    }

    @Override
    public Object run() {
        float score = 0;
        for (int i = 0; i < 10; i++) {
            score += Math.log(2);
        }
        return score;
    }

}
