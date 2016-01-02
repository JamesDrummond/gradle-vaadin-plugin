/*
* Copyright 2015 John Ahlroos
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
package fi.jasoft.plugin.tasks

import fi.jasoft.plugin.servers.ApplicationServer
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

public class RunTask extends DefaultTask {

    public static final String NAME = 'vaadinRun'

    def server

    public RunTask() {
        dependsOn(CompileWidgetsetTask.NAME)
        dependsOn(CompileThemeTask.NAME)
        description = 'Runs the Vaadin application on an embedded server'

        addShutdownHook {
            if(server){
                server.terminate()
            }
        }
    }

    @TaskAction
    public void run() {
        server = ApplicationServer.create(project)
        server.startAndBlock()
    }
}