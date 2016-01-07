/*
 * Copyright 2014-2015 Time Warner Cable, Inc.
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
package com.twcable.gradle.sling.osgi

import com.twcable.gradle.sling.SimpleSlingSupportFactory
import com.twcable.gradle.sling.SlingServersConfiguration
import groovy.transform.TypeChecked
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin

import static com.twcable.gradle.GradleUtils.extension
import static java.net.HttpURLConnection.HTTP_OK

/**
 * <h1>Plugin name</h1>
 * "com.twcable.cq-bundle"
 *
 * <h1>Description</h1>
 * Adds tasks for working with the OSGi bundle created by this Project's 'jar' task via the Sling HTTP interface.
 * <p>
 * The bundle's configuration is controlled by setting properties on the {@link SlingBundleConfiguration}.
 *
 * <h1>Tasks</h1>
 * <table>
 *   <tr><th>name</th><th>description</th></tr>
 *   <tr><td>uploadBundle</td><td>Upload the bundle to the servers</td></tr>
 *   <tr><td>startBundle</td><td>Start the bundle on the servers</td></tr>
 *   <tr><td>stopBundle</td><td>Stop the bundle on the servers</td></tr>
 *   <tr><td>removeBundle</td><td>Uninstalls and deletes the bundle from the servers</td></tr>
 *   <tr><td>showBundle</td><td>Output the JSON for the bundle's status</td></tr>
 *   <tr><td>refreshAllBundles</td><td>Refresh the dependencies for every bundle running on the servers</td></tr>
 * </table>
 *
 * @see SlingBundleConfiguration
 * @see SlingServersConfiguration
 */
@TypeChecked
@SuppressWarnings("GrMethodMayBeStatic")
class SlingBundlePlugin implements Plugin<Project> {

    @SuppressWarnings("GroovyUnusedAssignment")
    void apply(Project project) {
        project.plugins.apply(JavaPlugin)

        def existingTasks = project.tasks.asList() as Set

        uploadBundle(project)
        startBundle(project)
        stopBundle(project)
        removeBundle(project)
        showBundle(project)
        Task refreshAllBundlesTask = refreshAllBundles(project)

        extension(project, SlingServersConfiguration, project)

        def osgiBundle = extension(project, SlingProjectBundleConfiguration, project)
        osgiBundle.sourceFile // "prime" the "jar" task

        def serversConfiguration = project.extensions.getByType(SlingServersConfiguration)

        // apply configuration that applies to all these tasks
        project.tasks.withType(BasicBundleTask) { BasicBundleTask task ->
            task.group = 'OSGi'
            task.bundleAndServers = new BundleAndServers(osgiBundle, serversConfiguration)
        }

        // if the root project does not already have the "refreshAllBundles" task, attach it
        if (project.rootProject.tasks.findByName('refreshAllBundles') == null) {
            project.rootProject.tasks.add(refreshAllBundlesTask)
        }

        def allTasks = project.tasks.asList() as Set
        def newTasks = (allTasks - existingTasks) as Set<Task>

        //GradleUtils.taskDependencyGraph(project, newTasks)
    }


    private Task refreshAllBundles(Project project) {
        return project.tasks.create('refreshAllBundles', BasicBundleTask).with {
            description = 'Refreshes all the bundles in the Sling server'
            doLast {
                def resp = bundleAndServers.doAcrossServers(true) { SlingBundleSupport slingBundleSupport ->
                    bundleAndServers.refreshOsgiPackages(slingBundleSupport.slingSupport, slingBundleSupport.bundleServerConfiguration.bundlesControlUri)
                }
                if (resp.code != HTTP_OK) throw new GradleException("Server response: ${resp}")
            }
        }
    }


    private Task showBundle(Project project) {
        return project.tasks.create('showBundle', BasicBundleTask).with {
            description = 'Shows the bundle information in the authoring Sling server to STDOUT'
            doLast {
                def slingBundleSupport = SlingBundleSupport.create(bundleAndServers.slingBundleConfig,
                    bundleAndServers.serversConfiguration.first(), SimpleSlingSupportFactory.INSTANCE)
                println((String)bundleAndServers.getSlingBundleInformationJson(slingBundleSupport))
            }
        }
    }


    private Task removeBundle(Project project) {
        return project.tasks.create('removeBundle', BasicBundleTask).with {
            description = 'Uninstalls and deletes the bundle from Felix'
            dependsOn 'stopBundle'
            doLast {
                def resp = bundleAndServers.doAcrossServers(true) { SlingBundleSupport slingBundleSupport ->
                    def bundleLocation = bundleAndServers.getBundleLocation(slingBundleSupport)

                    def rc = slingBundleSupport.uninstallBundle()

                    if (rc.code == HTTP_OK && bundleLocation != null)
                        return slingBundleSupport.removeBundle(bundleLocation)
                    else
                        return rc
                }
                if (resp.code != HTTP_OK) throw new GradleException("Server response: ${resp}")
            }
        }
    }


    private Task stopBundle(Project project) {
        return project.tasks.create('stopBundle', BasicBundleTask).with {
            description = 'Stops bundle on the Sling server'
            doLast {
                def resp = bundleAndServers.doAcrossServers(true) { SlingBundleSupport slingBundleSupport ->
                    slingBundleSupport.stopBundle()
                }
                if (resp.code != HTTP_OK) throw new GradleException("Server response: ${resp}")
            }
        }
    }


    private Task startBundle(Project project) {
        return project.tasks.create('startBundle', BasicBundleTask).with {
            description = 'Starts the bundle in the Sling server'
            doLast {
                def resp = bundleAndServers.doAcrossServers(false) { SlingBundleSupport slingBundleSupport ->
                    slingBundleSupport.startBundle()
                }
                if (resp.code != HTTP_OK) throw new GradleException("Server response: ${resp}")
            }
        }
    }


    private Task uploadBundle(Project project) {
        return project.tasks.create('uploadBundle', BasicBundleTask).with {
            description = 'Upload the bundle to the Sling server'
            dependsOn 'jar', 'removeBundle'
            doLast {
                def resp = bundleAndServers.doAcrossServers(true) { SlingBundleSupport slingBundleSupport ->
                    slingBundleSupport.uploadBundle()
                }
                if (resp.code != HTTP_OK) throw new GradleException("Server response: ${resp}")
            }
        }
    }

}
