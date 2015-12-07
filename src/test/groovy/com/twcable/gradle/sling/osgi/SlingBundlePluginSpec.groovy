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

import com.twcable.gradle.sling.SlingServersConfiguration
import nebula.test.PluginProjectSpec

class SlingBundlePluginSpec extends PluginProjectSpec {

    @SuppressWarnings("GroovyMissingReturnStatement")
    def "check types"() {
        given:
        project.plugins.apply(SlingBundlePlugin)
        project.bundle.installPath = "/apps/gradle_test/install"

        expect:
        project.slingServers instanceof SlingServersConfiguration
        project.bundle instanceof SlingBundleConfiguration
        project.jar.archivePath == project.file("build/libs/${project.name}.jar")
        def bundle = project.bundle as SlingBundleConfiguration
        bundle.sourceFile == project.jar.archivePath
        bundle.bundlePath == "/apps/gradle_test/install/${project.name}.jar" as String
    }


    @Override
    String getPluginName() {
        return 'com.twcable.cq-bundle'
    }

}
