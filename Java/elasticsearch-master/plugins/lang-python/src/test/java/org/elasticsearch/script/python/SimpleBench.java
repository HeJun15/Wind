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

package org.elasticsearch.script.python;

import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.CompiledScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptService;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class SimpleBench {

    public static void main(String[] args) {
        PythonScriptEngineService se = new PythonScriptEngineService(Settings.Builder.EMPTY_SETTINGS);
        Object compiled = se.compile("x + y");
        CompiledScript compiledScript = new CompiledScript(ScriptService.ScriptType.INLINE, "SimpleBench", "python", compiled);


        Map<String, Object> vars = new HashMap<String, Object>();
        // warm up
        for (int i = 0; i < 1000; i++) {
            vars.put("x", i);
            vars.put("y", i + 1);
            se.executable(compiledScript, vars).run();
        }

        final long ITER = 100000;

        StopWatch stopWatch = new StopWatch().start();
        for (long i = 0; i < ITER; i++) {
            se.executable(compiledScript, vars).run();
        }
        System.out.println("Execute Took: " + stopWatch.stop().lastTaskTime());

        stopWatch = new StopWatch().start();
        ExecutableScript executableScript = se.executable(compiledScript, vars);
        for (long i = 0; i < ITER; i++) {
            executableScript.run();
        }
        System.out.println("Executable Took: " + stopWatch.stop().lastTaskTime());

        stopWatch = new StopWatch().start();
        executableScript = se.executable(compiledScript, vars);
        for (long i = 0; i < ITER; i++) {
            for (Map.Entry<String, Object> entry : vars.entrySet()) {
                executableScript.setNextVar(entry.getKey(), entry.getValue());
            }
            executableScript.run();
        }
        System.out.println("Executable (vars) Took: " + stopWatch.stop().lastTaskTime());
    }
}
