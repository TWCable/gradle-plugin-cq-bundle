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

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.sling.SlingBundleFixture
import com.twcable.gradle.sling.SlingServerFixture
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import groovy.transform.TypeChecked
import nebula.test.ProjectSpec
import org.gradle.api.plugins.JavaPlugin
import spock.lang.Subject

import static java.net.HttpURLConnection.HTTP_OK

@SuppressWarnings(["GroovyAssignabilityCheck", "GroovyAccessibility", "GroovyPointlessBoolean"])
class SlingBundleSupportSpec extends ProjectSpec {

    @Subject
    SlingBundleSupport slingBundleSupport

    private BundleServerConfiguration _serverConf
    private SlingSupport _slingSupport


    def setup() {
        project.plugins.apply(JavaPlugin)
        serversConfiguration.servers.each {
            def serverConfiguration = it.value
            serverConfiguration.maxWaitMs = 100
            serverConfiguration.retryWaitMs = 2
        }
        slingBundleSupport = new SlingBundleSupport(bundleConfiguration, bundleServerConf, slingSupport)
    }


    def "uninstall bundle"() {
        given:
        1 * slingSupport.doPost(_, { it['action'] == 'uninstall' }) >> okResp('{"state": "Active"}')

        expect:
        slingBundleSupport.uninstallBundle()
    }


    def "start bundle"() {
        given:
        stubPostAction 'start', '{"state": "Active"}'

        expect:
        slingBundleSupport.startBundle()
    }


    def "stop bundle"() {
        given:
        stubPostAction 'stop', '{"state": "Active"}'

        expect:
        slingBundleSupport.stopBundle()
    }


    def "refresh bundle"() {
        given:
        stubPostAction 'refresh', '{"state": "Active"}'

        expect:
        slingBundleSupport.refreshBundle()
    }


    def "update bundle"() {
        given:
        stubPostAction 'update', '{"state": "Active"}'

        expect:
        slingBundleSupport.updateBundle()
    }


    def "upload new bundle"() {
        given:
        slingSupport.makePath(_) >> okResp('')
        slingSupport.doPost(bundleConfiguration.getBundleInstallUrl(bundleServerConf.serverConf), _) >>
            okResp(new SlingBundleFixture(bundleConfiguration: bundleConfiguration).uploadFileResponse())

        when:
        final response = slingBundleSupport.uploadBundle()

        then:
        response.code == HTTP_OK
        (response as Map).path == bundleConfiguration.installPath
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


    @TypeChecked
    BundleServerConfiguration getBundleServerConf() {
        if (_serverConf == null) {
            _serverConf = new BundleServerConfiguration(serversConfiguration.first())
            if (_serverConf == null) throw new IllegalStateException("There are no active servers in ${serversConfiguration}")
        }
        return _serverConf
    }


    @TypeChecked
    void setSymbolicName(String symbolicName) {
        bundleConfiguration.symbolicName = symbolicName
    }


    @TypeChecked
    String getSymbolicName() {
        return bundleConfiguration.symbolicName
    }


    SlingSupport mockSlingSupport() {
        return Mock(SlingSupport) {
            getServerConf() >> bundleServerConf.serverConf
            0 * /do.*/(*_) // complain if any unexpected calls are made
        }
    }


    @TypeChecked
    SlingSupport getSlingSupport() {
        if (_slingSupport == null) {
            _slingSupport = mockSlingSupport()
        }
        return _slingSupport
    }


    void stubGet(URI uri, HttpResponse response) {
        slingSupport.doGet(uri) >> response
    }


    @TypeChecked
    HttpResponse bundlesResp(BundleState state) {
        return new HttpResponse(HTTP_OK, bundlesJson(state))
    }


    @TypeChecked
    String bundlesJson(BundleState state) {
        def bundleFixture = new SlingBundleFixture(bundleConfiguration: bundleConfiguration, bundleState: state)

        def serverFixture = new SlingServerFixture(bundles: [bundleFixture])

        return serverFixture.bundlesInformationJson(false)
    }


    @TypeChecked
    public HttpResponse okResp(String body) {
        return new HttpResponse(HTTP_OK, body)
    }


    @TypeChecked
    public URI getBundleUri() {
        return bundleConfiguration.getBundleUrl(getBundleServerConf())
    }


    void stubPostAction(String action, String resp) {
        slingSupport.doPost(bundleConfiguration.getBundleUrl(getBundleServerConf()), { it['action'] == action }) >>
            okResp(resp)
    }

}
