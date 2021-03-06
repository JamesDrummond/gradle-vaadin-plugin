/*
* Copyright 2017 John Ahlroos
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package fi.jasoft.plugin.configuration

/**
 * Configuration for the SASS Compiler
 */
@PluginConfiguration
class CompileThemeConfiguration {

    /**
     * Custom directory where themes can be found
     */
    String themesDirectory = null

    /**
     * Theme compiler to use
     * <p>
     *     Available options are
     *     <ul>
     *         <li>vaadin - Vaadin's SASS Compiler</li>
     *         <li>compass - Compass's SASS Compiler</li>
     *         <li>libsass - Libsass SASS Compiler</li>
     *     </ul>
     */
    String compiler = 'vaadin'

    /**
     * Use theme compression
     */
    boolean compress = true
}
