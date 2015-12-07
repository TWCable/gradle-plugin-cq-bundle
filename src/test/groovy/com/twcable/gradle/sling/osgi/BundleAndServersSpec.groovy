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
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServerFixture
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import groovy.transform.TypeChecked
import nebula.test.ProjectSpec
import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPlugin
import spock.lang.Subject

import static com.twcable.gradle.sling.osgi.BundleState.ACTIVE
import static com.twcable.gradle.sling.osgi.BundleState.FRAGMENT
import static com.twcable.gradle.sling.osgi.BundleState.INSTALLED
import static com.twcable.gradle.sling.osgi.BundleState.RESOLVED
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import static java.net.HttpURLConnection.HTTP_OK

@SuppressWarnings(["GroovyAssignabilityCheck", "GroovyPointlessArithmetic", "GroovyUntypedAccess", "GroovyPointlessBoolean"])
class BundleAndServersSpec extends ProjectSpec {
    @Subject
    BundleAndServers slingBundle

    private BundleServerConfiguration _serverConf
    private SlingSupport _slingSupport


    def setup() {
        project.plugins.apply(JavaPlugin)
        serversConfiguration.servers.each {
            def serverConfiguration = it.value
            serverConfiguration.maxWaitMs = 100
            serverConfiguration.retryWaitMs = 2
        }
        slingBundle = new BundleAndServers(bundleConfiguration, serversConfiguration)
    }


    def "uninstall bundle"() {
        given:
        1 * slingSupport.doPost(_, { it['action'] == 'uninstall' }) >> okResp('{"state": "Active"}')

        expect:
        slingBundle.uninstallBundle(slingBundleSupport)
    }


    def "start bundle"() {
        given:
        stubPostAction 'start', '{"state": "Active"}'

        expect:
        slingBundle.startBundle(slingBundleSupport)
    }


    def "stop bundle"() {
        given:
        stubPostAction 'stop', '{"state": "Active"}'

        expect:
        slingBundle.stopBundle(slingBundleSupport)
    }


    def "refresh bundle"() {
        given:
        stubPostAction 'refresh', '{"state": "Active"}'

        expect:
        slingBundle.refreshBundle(slingBundleSupport)
    }


    def "update bundle"() {
        given:
        stubPostAction 'update', '{"state": "Active"}'

        expect:
        slingBundle.updateBundle(slingBundleSupport)
    }


    def "upload new bundle"() {
        given:
        slingSupport.makePath(_) >> okResp('')
        slingSupport.doPost(bundleConfiguration.getBundleInstallUrl(bundleServerConf.serverConf), _) >>
            okResp(new SlingBundleFixture(bundleConfiguration: bundleConfiguration).uploadFileResponse())

        when:
        final response = slingBundle.uploadBundle(slingBundleSupport)

        then:
        response.code == HTTP_OK
        (response as Map).path == bundleConfiguration.installPath
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
        def resp = BundleAndServers.doAcrossServers(serversConfiguration, bundleConfiguration, slingSupportFactory) {}

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
        def resp = BundleAndServers.doAcrossServers(serversConfiguration, bundleConfiguration, slingSupportFactory) {}

        then:
        resp == new HttpResponse(HTTP_INTERNAL_ERROR, 'Boom')
    }


    def "validate all bundles: resolved -> active"() {
        given:
        symbolicName = 'b.c.d.e'

        4 * slingSupport.doGet(bundleServerConf.bundlesControlUri) >>> [bundlesResp(RESOLVED), bundlesResp(RESOLVED), bundlesResp(RESOLVED), bundlesResp(ACTIVE)]

        when:
        slingBundle.validateAllBundles([symbolicName], slingBundleSupport)

        then:
        true // the fact that no exception was thrown shows that it's good
    }


    def "start bundles"() {
        given:
        stubGet getBundleServerConf().bundlesControlUri, bundlesResp(INSTALLED)

        when:
        slingBundle.startInactiveBundles(slingBundleSupport)

        then:
        true // the fact that no exception was thrown shows that it's good
    }


    def "validate all bundles: resolved"() {
        given:
        stubGet bundleServerConf.bundlesControlUri, bundlesResp(RESOLVED)

        when:
        slingBundle.validateAllBundles(['b.c.d.e'], slingBundleSupport)

        then:
        def exp = thrown(GradleException)
        exp.message ==~ /Not all bundles .* are ACTIVE.*/
    }


    def "validate all bundles: installed"() {
        given:
        stubGet bundleServerConf.bundlesControlUri, bundlesResp(INSTALLED)

        when:
        slingBundle.validateAllBundles(['b.c.d.e'], slingBundleSupport)

        then:
        def exp = thrown(GradleException)
        exp.message ==~ /Not all bundles .* are ACTIVE.*/
    }


    def "validate all bundles: fragment"() {
        given:
        symbolicName = 'b.c.d.e'
        stubGet bundleServerConf.bundlesControlUri, bundlesResp(FRAGMENT)

        when:
        slingBundle.validateAllBundles([symbolicName], slingBundleSupport)

        then:
        true // the fact that no exception was thrown shows that it's good
    }


    def "validate all bundles: missing"() {
        given:
        def serverFixture = new SlingServerFixture(bundles: [new SlingBundleFixture(bundleConfiguration: bundleConfiguration)])

        stubGet bundleServerConf.bundlesControlUri, okResp(serverFixture.bundlesInformationJson(false))

        when:
        slingBundle.validateAllBundles(['b.c.d.e'], slingBundleSupport)

        then:
        def exp = thrown(GradleException)
        exp.message ==~ /Not all bundles .* are ACTIVE.*/
    }


    def "checkActiveBundles for ACTIVE"() {
        stubGet getBundleServerConf().bundlesControlUri, bundlesResp(ACTIVE)

        when:
        slingBundle.checkActiveBundles("com.test1", slingBundleSupport)

        then:
        true // the fact that no exception was thrown shows that it's good
    }


    def "checkActiveBundles for RESOLVED"() {
        symbolicName = 'com.test.d.e'

        stubGet bundleServerConf.bundlesControlUri, bundlesResp(RESOLVED)

        when:
        slingBundle.checkActiveBundles("com.test", slingBundleSupport)

        then:
        def exp = thrown(GradleException)
        exp.message.contains("Not all bundles are ACTIVE")
    }


    def "checkActiveBundles for TIMEOUT"() {
        stubGet bundleServerConf.bundlesControlUri, new HttpResponse(HTTP_CLIENT_TIMEOUT, '')

        when:
        slingBundle.checkActiveBundles("com.test", slingBundleSupport)

        then:
        bundleServerConf.serverConf.active == false
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
            return project.extensions.create(SlingBundleConfiguration.NAME, SlingBundleConfiguration, project)
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
    SlingBundleSupport getSlingBundleSupport() {
        return new SlingBundleSupport(bundleConfiguration, bundleServerConf, slingSupport)
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
