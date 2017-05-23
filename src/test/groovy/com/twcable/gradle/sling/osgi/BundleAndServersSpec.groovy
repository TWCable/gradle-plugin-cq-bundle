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
package com.twcable.gradle.sling.osgi

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import groovy.transform.TypeChecked
import nebula.test.ProjectSpec
import org.gradle.api.plugins.JavaPlugin
import spock.lang.Subject

import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static java.net.HttpURLConnection.HTTP_OK

@SuppressWarnings(["GroovyAssignabilityCheck", "GroovyPointlessArithmetic", "GroovyUntypedAccess", "GroovyPointlessBoolean"])
class BundleAndServersSpec extends ProjectSpec {
    @Subject
    BundleAndServers slingBundle


    def setup() {
        project.plugins.apply(JavaPlugin)
        serversConfiguration.servers.each {
            def serverConfiguration = it.value
            serverConfiguration.maxWaitMs = 100
            serverConfiguration.retryWaitMs = 2
        }
        slingBundle = new BundleAndServers(bundleConfiguration, serversConfiguration)
    }


    def "doAcrossServers handles 408 correctly"() {
        def slingSupportFactory = { SlingServerConfiguration sc ->
            def ss = Mock(SlingSupport)
            ss.serverConf >> sc
            if (sc.name == 'author')
                ss.doHttp(_) >> new HttpResponse(HTTP_OK, '')
            else
                ss.doHttp(_) >> new HttpResponse(HTTP_CLIENT_TIMEOUT, '')
            return ss
        }

        when:
        def resp = BundleAndServers.doAcrossServers(serversConfiguration, bundleConfiguration, slingSupportFactory, false) {
        }

        then:
        resp == new HttpResponse(HTTP_OK, '')
    }


    def "doAcrossServers handles a bad response correctly"() {
        def slingSupportFactory = { SlingServerConfiguration sc ->
            def ss = Mock(SlingSupport)
            ss.serverConf >> sc
            if (sc.name == 'author')
                ss.doHttp(_) >> new HttpResponse(HTTP_OK, '')
            else
                ss.doHttp(_) >> new HttpResponse(HTTP_INTERNAL_ERROR, 'Boom')
            return ss
        }

        when:
        def resp = BundleAndServers.doAcrossServers(serversConfiguration, bundleConfiguration, slingSupportFactory, false) {
        }

        then:
        resp == new HttpResponse(HTTP_INTERNAL_ERROR, 'Boom')
    }


    def "doAcrossServers missing bundle when missing is not OK"() {
        def slingSupportFactory = { SlingServerConfiguration sc ->
            def ss = Mock(SlingSupport)
            ss.serverConf >> sc
            if (sc.name == 'author')
                ss.doHttp(_) >> new HttpResponse(HTTP_OK, '')
            else
                ss.doHttp(_) >> new HttpResponse(HTTP_NOT_FOUND, 'Missing')
            return ss
        }

        when:
        def resp = BundleAndServers.doAcrossServers(serversConfiguration, bundleConfiguration, slingSupportFactory, false) {
        }

        then:
        resp == new HttpResponse(HTTP_NOT_FOUND, 'Missing')
    }


    def "doAcrossServers missing bundle when missing is OK"() {
        def slingSupportFactory = { SlingServerConfiguration sc ->
            def ss = Mock(SlingSupport)
            ss.serverConf >> sc
            if (sc.name == 'author')
                ss.doHttp(_) >> new HttpResponse(HTTP_OK, '')
            else
                ss.doHttp(_) >> new HttpResponse(HTTP_NOT_FOUND, 'Missing')
            return ss
        }

        when:
        def resp = BundleAndServers.doAcrossServers(serversConfiguration, bundleConfiguration, slingSupportFactory, true) {
        }

        then:
        resp == new HttpResponse(HTTP_OK, '')
    }

    // **********************************************************************
    //
    // HELPER METHODS
    //
    // **********************************************************************


    @TypeChecked
    SlingBundleConfiguration getBundleConfiguration() {
        def bundleConfiguration = project.extensions.findByType(SlingBundleConfiguration)
        if (bundleConfiguration == null) {
            return project.extensions.create(SlingProjectBundleConfiguration.NAME, SlingProjectBundleConfiguration, project)
        }
        return bundleConfiguration
    }


    @TypeChecked
    SlingServersConfiguration getServersConfiguration() {
        def serversConfiguration = project.extensions.findByType(SlingServersConfiguration)
        if (serversConfiguration == null) {
            return project.extensions.create(SlingServersConfiguration.NAME, SlingServersConfiguration, project)
        }
        return serversConfiguration
    }

}
