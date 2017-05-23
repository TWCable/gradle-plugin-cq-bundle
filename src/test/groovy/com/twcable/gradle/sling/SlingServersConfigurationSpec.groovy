/*
 * Copyright 2014-2017 Time Warner Cable, Inc.
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
package com.twcable.gradle.sling

import nebula.test.ProjectSpec
import org.gradle.api.internal.ExtensibleDynamicObject
import org.gradle.api.internal.project.DefaultProject
import spock.lang.Subject

@Subject(SlingServersConfiguration)
class SlingServersConfigurationSpec extends ProjectSpec {

    def setup() {
        clearProperties()
    }


    def "has expected servers as properties"() {
        given:
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        expect:
        servers.author
        servers.publisher
    }


    def "set server configurations based on project properties"() {
        given:
        addPropertiesToProject([
            "slingserver.author.port"        : 4302,
            "slingserver.author1.port"       : "4702", // check type-coercion
            "slingserver.author1.machineName": "testing"
        ])

        def envVars = [
            SLINGSERVER_PUBLISHER_PROTOCOL: 'https',
            SLINGSERVER_ENV_RETRY_MS      : '756',
            SLINGSERVER_AUTHOR_RETRY_MS   : '456',
            SLINGSERVER_AUTHOR_MAX_MS     : '34567',
            SLINGSERVER_AUTHOR_PORT       : '4444', // should not be used since proj prop will override
        ]

        when:
        def serversConf = new SlingServersConfiguration(project, envVars)

        then:
        serversConf.servers.collect { it.key } as Set == ['author', 'author1', 'publisher'] as Set
        serversConf.author.port == 4302
        serversConf.author.machineName == "localhost"
        serversConf.author.retryWaitMs == 456
        serversConf.author.maxWaitMs == 34_567
        serversConf.author.active == true
        serversConf.author1.port == 4702
        serversConf.author1.machineName == "testing"
        serversConf.author1.retryWaitMs == 756 // set by "env" namespace
        serversConf.author1.maxWaitMs == 10_000 // default
        serversConf.publisher.protocol == "https"
    }


    def "has expected servers as key lookup"() {
        given:
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        expect:
        servers['author']
        servers['publisher']
    }


    def "can iterate over expected servers"() {
        given:
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        expect:
        servers.collect { it.name }.containsAll(['author', 'publisher'])
    }

    // **********************************************************************
    //
    // HELPER METHODS
    //
    // **********************************************************************


    public void clearProperties() {
        System.clearProperty("envJson");
        System.clearProperty("environment");
    }


    void addPropertiesToProject(Map propertiesToAdd) {
        DefaultProject p = (DefaultProject)project
        ((ExtensibleDynamicObject)p.asDynamicObject).addProperties(propertiesToAdd)
    }

}
