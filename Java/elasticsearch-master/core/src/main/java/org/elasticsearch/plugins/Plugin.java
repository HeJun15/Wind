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

package org.elasticsearch.plugins;

import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.index.IndexService;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An extension point allowing to plug in custom functionality.
 * <p>
 * A plugin can be register custom extensions to builtin behavior by implementing <tt>onModule(AnyModule)</tt>,
 * and registering the extension with the given module.
 */
public abstract class Plugin {

    /**
     * The name of the plugin.
     */
    public abstract String name();

    /**
     * The description of the plugin.
     */
    public abstract String description();

    /**
     * Node level modules.
     */
    public Collection<Module> nodeModules() {
        return Collections.emptyList();
    }

    /**
     * Node level services that will be automatically started/stopped/closed.
     */
    public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        return Collections.emptyList();
    }

    /**
     * Additional node settings loaded by the plugin. Note that settings that are explicit in the nodes settings can't be
     * overwritten with the additional settings. These settings added if they don't exist.
     */
    public Settings additionalSettings() {
        return Settings.Builder.EMPTY_SETTINGS;
    }

    /**
     * Called once the given {@link IndexService} is fully constructed but not yet published.
     * This is used to initialize plugin services that require acess to index level resources
     */
    public void onIndexService(IndexService indexService) {}

    /**
     * Called before a new index is created on a node. The given module can be used to regsiter index-leve
     * extensions.
     */
    public void onIndexModule(IndexModule indexModule) {}

    /**
     * Old-style guice index level extension point.
     *
     * @deprecated use #onIndexModule instead
     */
    @Deprecated
    public final void onModule(IndexModule indexModule) {}
}
