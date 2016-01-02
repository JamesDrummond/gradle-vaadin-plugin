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
package fi.jasoft.plugin

import fi.jasoft.plugin.servers.ApplicationServer
import fi.jasoft.plugin.tasks.CreateDirectoryZipTask
import fi.jasoft.plugin.tasks.CreateWidgetsetGeneratorTask
import fi.jasoft.plugin.testbench.TestbenchHub
import fi.jasoft.plugin.testbench.TestbenchNode
import groovy.transform.PackageScope
import groovy.xml.MarkupBuilder
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.bundling.War
import org.gradle.plugins.ide.eclipse.model.EclipseWtp

class TaskListener implements TaskExecutionListener {

    private TestbenchHub testbenchHub

    private TestbenchNode testbenchNode

    ApplicationServer testbenchAppServer

    public void beforeExecute(Task task) {
        if (!task.project.hasProperty('vaadin')) {
            return
        }

        switch (task.name) {
            case 'eclipseClasspath':
                configureEclipsePlugin(task)
                break
            case 'ideaModule':
                configureIdeaModule(task)
                break
            case 'eclipseWtpFacet':
                configureEclipseWtpPluginFacet(task)
                break
            case 'eclipseWtpComponent':
                configureEclipseWtpPluginComponent(task)
                break
            case 'compileJava':
                ensureWidgetsetGeneratorExists(task)
                break
            case 'jar':
                configureAddonMetadata(task)
                break
            case 'war':
                configureJRebel(task)
                configureWAR(task)
                break
            case 'test':
                configureTest(task, this)
                break
            case 'javadoc':
                configureJavadoc(task)
                break
            case CreateDirectoryZipTask.NAME:
                configureAddonZipMetadata(task)
                break
        }
    }

    public void afterExecute(Task task, TaskState state) {
        if (!task.project.hasProperty('vaadin')) {
            return
        }

        if (task.name == 'test') {
            terminateTestbench(this)
        }

        // Notify users that sources are not present in the jar
        if (task.name == 'jar' && !state.getSkipped()) {
            task.getLogger().warn("Please note that the jar archive will NOT by default include the source files.\n" +
                    "You can add them to the jar by adding jar{ from sourceSets.main.allJava } to build.gradle.")
        }
    }

    @PackageScope
    static configureEclipsePlugin(Task task) {
        def project = task.project
        def conf = project.configurations
        def cp = project.eclipse.classpath

        // Always download sources
        cp.downloadSources = true

        // Set Eclipse's class output dir
        if (project.vaadin.plugin.eclipseOutputDir == null) {
            cp.defaultOutputDir = project.sourceSets.main.output.classesDir
        }
        else {
            cp.defaultOutputDir = project.file(project.vaadin.plugin.eclipseOutputDir)
        }

        // Add dependencies to eclipse classpath
        cp.plusConfigurations += [conf[GradleVaadinPlugin.CONFIGURATION_SERVER]]
        cp.plusConfigurations += [conf[GradleVaadinPlugin.CONFIGURATION_CLIENT]]

        if (project.vaadin.testbench.enabled) {
            cp.plusConfigurations += [conf[GradleVaadinPlugin.CONFIGURATION_TESTBENCH]]
        }

        if (Util.isPushSupportedAndEnabled(project)) {
            cp.plusConfigurations += [conf[GradleVaadinPlugin.CONFIGURATION_PUSH]]
        }

        // Configure natures
        def natures = project.eclipse.project.natures
        natures.add(0, 'org.springsource.ide.eclipse.gradle.core.nature' )
    }

    @PackageScope
    static configureIdeaModule(Task task) {
        def project = task.project
        def conf = project.configurations
        def module = project.idea.module

        // Module name is project name
        module.name = project.name

        module.inheritOutputDirs = false
        module.outputDir = project.sourceSets.main.output.classesDir
        module.testOutputDir = project.sourceSets.test.output.classesDir

        // Download sources and javadoc
        module.downloadJavadoc = true
        module.downloadSources = true

        // Add configurations to classpath
        module.scopes.COMPILE.plus += [conf[GradleVaadinPlugin.CONFIGURATION_SERVER]]
        module.scopes.COMPILE.plus += [conf[GradleVaadinPlugin.CONFIGURATION_CLIENT]]

        if (project.vaadin.testbench.enabled) {
            module.scopes.TEST.plus += [conf[GradleVaadinPlugin.CONFIGURATION_TESTBENCH]]
        }

        if (Util.isPushSupportedAndEnabled(project)) {
            module.scopes.COMPILE.plus += [conf[GradleVaadinPlugin.CONFIGURATION_PUSH]]
        }
    }

    @PackageScope
    static configureEclipseWtpPluginComponent(Task task) {
        def project = task.project
        def wtp = project.eclipse.wtp
        wtp.component.plusConfigurations += [project.configurations[GradleVaadinPlugin.CONFIGURATION_SERVER]]
    }

    @PackageScope
    static configureEclipseWtpPluginFacet(Task task) {
        def wtp = task.project.eclipse.wtp as EclipseWtp
        def facet = wtp.facet

        facet.facets = []
        facet.facet(name: 'jst.web', version: '3.0')
        facet.facet(name: 'jst.java', version: task.project.sourceCompatibility)
        facet.facet(name: 'com.vaadin.integration.eclipse.core', version: '7.0')
        facet.facet(name: 'java', version: task.project.sourceCompatibility)
    }

    @PackageScope
    static ensureWidgetsetGeneratorExists(Task task) {
        def generator = task.project.vaadin.widgetsetGenerator
        if (generator != null) {
            String name = generator.tokenize('.').last()
            String pkg = generator.replaceAll('.' + name, '')
            String filename = name + ".java"
            File javaDir = Util.getMainSourceSet(task.project).srcDirs.iterator().next()
            File f = task.project.file(javaDir.canonicalPath + '/' + pkg.replaceAll(/\./, '/') + '/' + filename)
            if (!f.exists()) {
                task.project.tasks[CreateWidgetsetGeneratorTask.NAME].run()
            }
        }
    }

    @PackageScope
    static File getManifest(Task task) {
        def project = task.project
        def sources = Util.getMainSourceSet(project).srcDirs.asList() + project.sourceSets.main.resources.srcDirs.asList()
        File manifest = null
        sources.each {
            project.fileTree(it).matching({
                include '**/META-INF/MANIFEST.MF'
            }).each {
                manifest = it
            }
        }
        return manifest
    }

    @PackageScope
    static configureAddonMetadata(Task task) {
        def project = task.project

        // Resolve widgetset
        def widgetset = project.vaadin.widgetset
        if (widgetset == null) {
            widgetset = GradleVaadinPlugin.DEFAULT_WIDGETSET
        }

        // Scan for existing manifest in source folder and reuse if possible
        File manifest = getManifest(task)
        if (manifest != null) {
            project.logger.warn("Manifest found in project, possibly overwriting existing values.")
            task.manifest.from(manifest)
        }

        //Validate values
        if (project.vaadin.addon.title == '') {
            project.logger.warn("No vaadin.addon.title has been specified, jar not compatible with Vaadin Directory.")
        }

        if (project.version == 'unspecified') {
            project.logger.warn("No version specified for the project, jar not compatible with Vaadin Directory.")
        }

        // Get stylesheets
        def styles = Util.findAddonSassStylesInProject(project)
        if(project.vaadin.addon.styles != null){
            project.vaadin.addon.styles.each({ path ->
                if(path.endsWith('scss') || path.endsWith('.css')){
                    styles.add(path)
                } else {
                    project.logger.warn("Could not add '"+path+"' to jar manifest. Only CSS and SCSS files are supported as addon styles.")
                }
            })
        }

        // Add metadata to jar manifest
        def attributes = [:]
        attributes['Vaadin-Package-Version'] = 1
        attributes['Implementation-Version'] = project.version
        attributes['Built-By'] = "Gradle Vaadin Plugin ${GradleVaadinPlugin.PLUGIN_VERSION}"
        if(widgetset){
            attributes['Vaadin-Widgetsets'] = widgetset
        }
        if(styles){
           attributes['Vaadin-Stylesheets'] = styles.join(',')
        }
        if(project.vaadin.addon.license){
            attributes['Vaadin-License-Title'] = project.vaadin.addon.license
        }
        if(project.vaadin.addon.title){
            attributes['Implementation-Title'] = project.vaadin.addon.title
        }
        if(project.vaadin.addon.author){
            attributes['Implementation-Vendor'] = project.vaadin.addon.author
        }
        task.manifest.attributes(attributes)
    }

    @PackageScope
    static configureAddonZipMetadata(Task task) {
        def project = task.project
        def attributes = [
                'Vaadin-License-Title': project.vaadin.addon.license,
                'Implementation-Title': project.vaadin.addon.title,
                'Implementation-Version': project.version != null ? project.version : '',
                'Implementation-Vendor': project.vaadin.addon.author,
                'Vaadin-Addon': "libs/${project.jar.archiveName}"
        ] as HashMap<String, String>

        // Create metadata file
        def buildDir = project.file('build/tmp/zip')
        buildDir.mkdirs()

        def meta = project.file(buildDir.absolutePath + '/META-INF')
        meta.mkdirs()

        def manifestFile = project.file(meta.absolutePath + '/MANIFEST.MF')
        manifestFile.createNewFile()
        manifestFile << attributes.collect { key, value -> "$key: $value" }.join("\n")
    }

    @PackageScope
    static configureJRebel(Task task) {
        def project = task.project
        if (project.vaadin.jrebel.enabled) {

            def classes = project.sourceSets.main.output.classesDir

            // Ensure classes dir exists
            classes.mkdirs();

            File rebelFile = project.file(classes.absolutePath + '/rebel.xml')

            def srcWebApp = project.webAppDir.absolutePath
            def writer = new FileWriter(rebelFile)

            new MarkupBuilder(writer).application() {
                classpath {
                    dir(name: classes.absolutePath)
                }
                web {
                    link(target: '/') {
                        dir(name: srcWebApp)
                    }
                }
            }
        }
    }

    @PackageScope
    static configureWAR(Task task){
        assert task instanceof War
        War war = (War) task;
        war.exclude('VAADIN/gwt-unitCache/**')
        if (task.project.vaadin.manageDependencies) {
            war.classpath = Util.getWarClasspath(task.project).files
        }
    }

    @PackageScope
    static configureTest(Task task, TaskListener listener){
        def project = task.project
        if(project.vaadin.testbench.enabled){
            if (project.vaadin.testbench.hub.enabled) {
                listener.testbenchHub = new TestbenchHub(project)
                listener.testbenchHub.start()
            }

            if (project.vaadin.testbench.node.enabled) {
                listener.testbenchNode = new TestbenchNode(project)
                listener.testbenchNode.start()
            }

            if (project.vaadin.testbench.runApplication) {
                listener.testbenchAppServer = ApplicationServer.create(project)
                listener.testbenchAppServer.start()

                // Ensure everything is up and running before continuing with the tests
                sleep(5000)
            }
        }
    }

    @PackageScope
    static terminateTestbench(TaskListener listener) {
        if (listener.testbenchAppServer) {
            listener.testbenchAppServer.terminate()
            listener.testbenchAppServer = null
        }

        if (listener.testbenchNode) {
            listener.testbenchNode.terminate()
            listener.testbenchNode = null
        }

        if (listener.testbenchHub) {
            listener.testbenchHub.terminate()
            listener.testbenchHub = null
        }
    }

    @PackageScope
    static configureJavadoc(Task task){
        def project = task.project
        task.source = Util.getMainSourceSet(project)
        if (project.configurations.findByName(GradleVaadinPlugin.CONFIGURATION_JAVADOC)) {
            task.classpath = task.classpath.plus(project.configurations[GradleVaadinPlugin.CONFIGURATION_JAVADOC])
        }
        if (project.configurations.findByName(GradleVaadinPlugin.CONFIGURATION_SERVER)) {
            task.classpath = task.classpath.plus(project.configurations[GradleVaadinPlugin.CONFIGURATION_SERVER])
        }
        task.failOnError = false
        task.options.addStringOption("sourcepath", "")
    }

}