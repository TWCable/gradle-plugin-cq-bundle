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
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SimpleSlingSupportFactory
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import com.twcable.gradle.sling.SlingSupportFactory
import groovy.json.JsonSlurper
import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.apache.http.entity.mime.content.FileBody
import org.gradle.api.GradleException

import javax.annotation.Nonnull
import javax.annotation.Nullable

import static BundleState.ACTIVE
import static BundleState.FRAGMENT
import static BundleState.INSTALLED
import static BundleState.MISSING
import static BundleState.RESOLVED
import static SlingSupport.block
import static java.net.HttpURLConnection.HTTP_BAD_METHOD
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static java.net.HttpURLConnection.HTTP_OK

/**
 * Brings together a bundle configuration and a set of Sling servers to provides for interaction via the Sling REST API.
 */
@Slf4j
@TypeChecked
@SuppressWarnings(["GroovyPointlessBoolean", "GrMethodMayBeStatic"])
class BundleAndServers {
    //
    // Many of the commands for interacting with Sling/Felix come from the documentation at
    // http://felix.apache.org/site/web-console-restful-api.html
    //

    /**
     * The configuration of the OSGi bundle to interact with the server
     */
    final SlingBundleConfiguration slingBundleConfig

    /**
     * The configuration of the servers to interact with for the bundle
     */
    final SlingServersConfiguration serversConfiguration

    /**
     * @param slingBundleConfig configuration of the OSGi bundle to interact with the server
     * @param serversConfiguration configuration of the servers to interact with for the bundle
     */
    BundleAndServers(SlingBundleConfiguration slingBundleConfig, SlingServersConfiguration serversConfiguration) {
        this.slingBundleConfig = slingBundleConfig
        this.serversConfiguration = serversConfiguration
    }


    @Nonnull
    HttpResponse stopBundle(@Nonnull SlingBundleSupport slingSupport) {
        final url = slingBundleConfig.getBundleUrl(slingSupport.bundleServerConfiguration)
        return slingSupport.doPost(url, ['action': 'stop'])
    }


    @Nonnull
    HttpResponse startBundle(@Nonnull SlingBundleSupport slingSupport) {
        final url = slingBundleConfig.getBundleUrl(slingSupport.bundleServerConfiguration)
        return slingSupport.doPost(url, ['action': 'start'])
    }


    @Nonnull
    void startInactiveBundles(@Nonnull SlingBundleSupport slingBundleSupport) {
        def resp = slingBundleSupport.doGet(slingBundleSupport.bundlesControlUri)

        if (resp.code == HTTP_OK) {
            Map json = new JsonSlurper().parseText(resp.body) as Map
            List<Map> data = json.data as List

            def inactiveTwcBundles = data.findAll {
                it.state == RESOLVED.stateString
            }.collect { [it.id, it.symbolicName] }

            inactiveTwcBundles.each { id, symbolicName ->
                URI url = new URI("${slingBundleSupport.bundleControlBaseUri}/${id}.json")

                slingBundleSupport.doPost(url, ['action': 'start'])
                log.info "Trying to start inactive bundle: ${id}:${symbolicName}"
            }
        }
    }


    @Nonnull
    HttpResponse refreshBundle(@Nonnull SlingBundleSupport slingSupport) {
        final url = slingBundleConfig.getBundleUrl(slingSupport.bundleServerConfiguration)
        return slingSupport.doPost(url, ['action': 'refresh'])
    }


    @Nonnull
    HttpResponse updateBundle(@Nonnull SlingBundleSupport slingSupport) {
        final url = slingBundleConfig.getBundleUrl(slingSupport.bundleServerConfiguration)
        return slingSupport.doPost(url, ['action': 'update'])
    }

    /**
     * Runs the given server action across all the active servers
     *
     * @param missingIsOk is a 404 response considered OK? If false, it counts as an error
     * @param serverAction the action to run against the bundle on the server
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    @Nonnull
    HttpResponse doAcrossServers(boolean missingIsOk,
                                 BundleServerAction serverAction) {
        return doAcrossServers(serversConfiguration, slingBundleConfig, missingIsOk, serverAction)
    }

    /**
     * Runs the given server action across all the provided active servers
     *
     * @param servers the collection of servers to run the action across
     * @param bundleConfiguration the bundle's configuration to use for the action
     * @param missingIsOk is a 404 response considered OK? If false, it counts as an error
     * @param serverAction the action to run against the bundle on the server
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    @Nonnull
    static HttpResponse doAcrossServers(SlingServersConfiguration servers,
                                        SlingBundleConfiguration bundleConfiguration,
                                        boolean missingIsOk,
                                        BundleServerAction serverAction) {
        return doAcrossServers(servers, bundleConfiguration, SimpleSlingSupportFactory.INSTANCE, missingIsOk, serverAction)
    }

    /**
     * Runs the given server action across all the provided active servers
     *
     * @param servers the collection of servers to run the action across
     * @param bundleConfiguration the bundle's configuration to use for the action
     * @param slingSupportFactory the factory for creating the connection helper
     * @param missingIsOk is a 404 response considered OK? If false, it counts as an error
     * @param serverAction the action to run against the bundle on the server
     *
     * @return the "aggregate" HTTP response: if all the calls are in the >= 200 and <400 range, or
     * a 408 (timeout, server not running) the returns an empty HTTP_OK; otherwise returns the first error response
     * it came across
     */
    @Nonnull
    static HttpResponse doAcrossServers(SlingServersConfiguration servers,
                                        SlingBundleConfiguration bundleConfiguration,
                                        SlingSupportFactory slingSupportFactory,
                                        boolean missingIsOk,
                                        BundleServerAction serverAction) {
        def httpResponse = new HttpResponse(HTTP_OK, '')

        servers.each { serverConfig ->
            def slingSupport = slingSupportFactory.create(serverConfig)
            def bundleServerConf = new BundleServerConfiguration(serverConfig)
            def slingBundleSupport = new SlingBundleSupport(bundleConfiguration, bundleServerConf, slingSupport)
            if (serverConfig.active) {
                def resp = slingSupport.doHttp { SimpleHttpClient httpClient ->
                    serverAction.run(slingBundleSupport)
                }

                // restrictive: if any call is bad, the result is bad
                def gotBadResponse = isBadResponse(resp.code, missingIsOk)
                if (gotBadResponse) {
                    log.info "Received a bad response from ${serverConfig.name}: ${resp}"
                    httpResponse = resp
                }
            }
        }
        return httpResponse
    }


    static boolean isBadResponse(int respCode, boolean missingIsOk) {
        if (respCode == HTTP_NOT_FOUND) return !missingIsOk

        if (respCode >= HTTP_OK) {
            if (respCode < HTTP_BAD_REQUEST) return false
            if (respCode == HTTP_CLIENT_TIMEOUT) return false
            return true
        }
        return true
    }


    @Nonnull
    HttpResponse getSlingBundleInformationResp(SlingBundleSupport slingSupport) {
        return getTheSlingBundleInformation(slingBundleConfig.symbolicName, slingSupport)
    }


    @Nonnull
    static HttpResponse getTheSlingBundleInformation(String symbolicName, SlingBundleSupport slingSupport) {
        final url = SlingBundleConfiguration.getTheBundleUrl(symbolicName, slingSupport.bundleServerConfiguration)
        if (url != null) {
            return slingSupport.doGet(url)
        }
        else {
            return new HttpResponse(HTTP_BAD_REQUEST,
                "Can not get information on ${symbolicName} - it is probably not installed")
        }
    }


    @Nonnull
    String getSlingBundleInformationJson(SlingBundleSupport slingBundleSupport) {
        final resp = slingBundleSupport.slingSupport.doHttp { SimpleHttpClient httpClient ->
            getSlingBundleInformationResp(slingBundleSupport)
        }

        return (resp.code == HTTP_OK) ? resp.body : "${resp.code}: ${resp.body}"
    }

    /**
     * Returns the URL to the bundle file
     *
     * @return null if it can't determine the location
     */
    @Nullable
    URI getBundleLocation(SlingBundleSupport slingSupport) {
        return getTheBundleLocation(slingSupport)
    }

    /**
     * Returns the URL to the bundle file
     *
     * @return null if it can't determine the location
     */
    @Nullable
    static URI getTheBundleLocation(@Nonnull SlingBundleSupport slingSupport) {
        def bundleConfiguration = slingSupport.bundleConfiguration
        def symbolicName = bundleConfiguration.symbolicName
        URI bundleUri = bundleConfiguration.getBundleUrl(slingSupport.bundleServerConfiguration)

        final httpResponse = getTheSlingBundleInformation(symbolicName, slingSupport)

        if (httpResponse.code == HTTP_NOT_FOUND) {
            log.info "Could not find ${symbolicName} on ${slingSupport.serverConf.name}"
            return null
        }

        if (httpResponse.code == HTTP_OK) {
            try {
                Map json = new JsonSlurper().parseText(httpResponse.body) as Map

                List<Map> dataProps = ((Map)((List)json.data)[0]).props as List<Map>
                final location = dataProps.find { it.key == 'Bundle Location' }

                if (location == null) {
                    log.warn "Could not find a Bundle Location for ${symbolicName}"
                    return null
                }

                def fileLocation = location.value as String

                if (fileLocation.startsWith("inputstream:")) {
                    log.info "${symbolicName} is not stored in the JCR"
                    return null
                }

                if (fileLocation.startsWith("jcrinstall:")) {
                    // it's stored in the JCR
                    String filePath = URI.create(fileLocation).path
                    return new URI(bundleUri.scheme, bundleUri.userInfo, bundleUri.host, bundleUri.port, filePath, null, null)
                }

                log.warn "Don't know what to do with ${fileLocation}"
                return null
            }
            catch (Exception exp) {
                throw new GradleException("Could not read JSON from ${bundleUri}: \"${httpResponse.body}\"", exp)
            }
        }
        else {
            throw new GradleException("Problem getting bundle location from ${bundleUri} - ${httpResponse.code}: ${httpResponse.body}")
        }
    }


    @Nonnull
    static HttpResponse refreshOsgiPackages(@Nonnull SlingSupport slingSupport, @Nonnull URI bundleControlUri) {
        return slingSupport.doPost(bundleControlUri, ['action': 'refreshPackages'])
    }


    @Nonnull
    HttpResponse uninstallBundle(@Nonnull SlingBundleSupport slingSupport) {
        final url = slingBundleConfig.getBundleUrl(slingSupport.bundleServerConfiguration)
        return slingSupport.doPost(url, ['action': 'uninstall'])
    }


    @Nonnull
    HttpResponse removeBundle(SlingSupport slingSupport, URI bundleLocation) {
        return slingSupport.doPost(bundleLocation, [':operation': 'delete'])
    }


    @Nonnull
    HttpResponse uploadBundle(@Nonnull SlingBundleSupport slingSupport) {
        def serverConfiguration = slingSupport.serverConf
        if (!serverConfiguration.active) throw new IllegalArgumentException("serverConfiguration ${serverConfiguration.name} is not active")

        final installUri = slingBundleConfig.getBundleInstallUrl(serverConfiguration)
        final sourceFile = slingBundleConfig.sourceFile

        if (slingSupport.slingSupport.makePath(installUri)) {
            String filename = sourceFile.name
            log.info("Uploading ${filename} to ${installUri}")
            def resp = slingSupport.doPost(installUri, [
                (filename): new FileBody(sourceFile, 'application/java-archive'),
            ])
            log.info("Finished upload of ${filename} to ${installUri}")
            return resp
        }
        else {
            return new HttpResponse(HTTP_BAD_METHOD, 'Could not create area to put file in')
        }
    }


    void validateAllBundles(@Nonnull List<String> symbolicNames, @Nonnull SlingBundleSupport slingBundleSupport) {
        def serverConf = slingBundleSupport.serverConf
        def serverName = serverConf.name
        log.info "Checking for NON-ACTIVE bundles on ${serverName}"

        final pollingTxt = new DotPrinter()
        boolean bundlesActive = false

        block(
            serverConf.maxWaitMs,
            { serverConf.active && bundlesActive == false },
            {
                log.info pollingTxt.increment()

                def resp = slingBundleSupport.doGet(slingBundleSupport.bundlesControlUri)
                if (resp.code == HTTP_OK) {
                    try {
                        def json = new JsonSlurper().parseText(resp.body) as Map
                        List<Map<String, Object>> data = json.data as List

                        def knownBundles = data.findAll { Map b -> symbolicNames.contains(b.symbolicName) }
                        def knownBundleNames = knownBundles.collect { Map b -> (String)b.symbolicName }
                        def missingBundleNames = (symbolicNames - knownBundleNames)
                        def missingBundles = missingBundleNames.collect { String name ->
                            [symbolicName: name, state: MISSING.stateString] as Map<String, Object>
                        }
                        def allBundles = knownBundles + missingBundles

                        if (!hasAnInactiveBundle(allBundles)) {
                            if (log.debugEnabled) allBundles.each { Map b -> log.debug "Active bundle: ${b.symbolicName}" }
                            bundlesActive = true
                        }
                    }
                    catch (Exception exp) {
                        throw new GradleException("Problem parsing \"${resp.body}\"", exp)
                    }
                }
                else {
                    if (resp.code == HTTP_CLIENT_TIMEOUT)
                        serverConf.active = false
                    else
                        throw new GradleException("Could not get bundle data. ${resp.code}: ${resp.body}")
                }
            },
            serverConf.retryWaitMs
        )

        if (serverConf.active == false) return

        if (bundlesActive == false)
            throw new GradleException("Not all bundles for ${symbolicNames} are ACTIVE on ${serverName}")
        else
            log.info("Bundles are ACTIVE on ${serverName}")
    }

    /**
     * If any of the bundles in the given parsed status JSON are inactive and their symbolic name contains
     * "groupProperty", then returns false; otherwise returns true
     * @param json the parsed JSON for the status of all bundles JSON
     * @param groupProperty the part of the symbolic name to look for (e.g., "com.myco")
     */
    @SuppressWarnings("GroovyTrivialIf")
    private static boolean areAllBundlesActive(Map json, String groupProperty) {
        // Reading Json response for bundle status
        // The status response is an array like "s": [ 84, 81, 3, 0, 0 ],
        // Status number described as: [bundles existing, active, fragment, resolved, installed]
        // Ref Url: http://felix.apache.org/documentation/subprojects/apache-felix-web-console/web-console-restful-api.html

        def statuses = json.s as List
        def resolved = statuses[3] as Integer
        def installed = statuses[4] as Integer

        if (resolved == 0 && installed == 0) {
            log.debug "There are no bundles in the \"resolved\" or \"installed\" state"
            return true
        }

        List<Map> data = json.data as List

        def inactiveBundles = inactiveBudles(data).collect { it.symbolicName } as List<String>

        if (log.infoEnabled) inactiveBundles.each { log.info "Inactive bundle: ${it}" }

        if (inactiveBundles.isEmpty() || !inactiveBundles.any { it.contains(groupProperty) })
            return true
        else
            return false
    }


    private static Collection<Map> inactiveBudles(Collection<Map> knownBundles) {
        return knownBundles.findAll { bundle ->
            bundle.state == INSTALLED.stateString ||
                bundle.state == RESOLVED.stateString ||
                bundle.state == MISSING.stateString
        } as Collection<Map>
    }


    private boolean hasAnInactiveBundle(final Collection<Map<String, Object>> knownBundles) {
        final activeBundles = knownBundles.findAll { bundle ->
            bundle.state == ACTIVE.stateString ||
                bundle.state == FRAGMENT.stateString
        } as Collection<Map>

        final inactiveBundles = inactiveBudles(knownBundles)

        if (log.infoEnabled) inactiveBundles.each { log.info("bundle ${it.symbolicName} NOT active: ${it.state}") }
        if (log.debugEnabled) activeBundles.each { log.debug("bundle ${it.symbolicName} IS active") }

        inactiveBundles.size() > 0
    }

    /**
     * Given a list of symbolic names on a server, uninstalls them if they match the predicate
     *
     * @param symbolicNames the symbolic names on a server to check against
     * @param slingSupport the SlingSupport for a particular server
     * @param predicate the predicate determine if the bundle should be uninstalled
     */
    // TODO: This is used by the cq-package plugin; should probably be moved as a task into this plugin
    void uninstallAllBundles(@Nonnull List<String> symbolicNames,
                             @Nonnull SlingBundleSupport slingSupport,
                             @Nullable UninstallBundlePredicate predicate) {
        log.info "Uninstalling/removing bundles on ${slingSupport.serverConf.name}: ${symbolicNames}"

        symbolicNames.each { String symbolicName ->
            if (predicate != null && predicate.eval(symbolicName)) {
                log.info "Stopping $symbolicName on ${slingSupport.serverConf.name}"
                stopBundle(slingSupport)
                log.info "Uninstalling $symbolicName on ${slingSupport.serverConf.name}"
                uninstallBundle(slingSupport)
            }
        }
    }

    /**
     * If any of the bundles on the server pointed to by "slingBundleSupport" are inactive and their symbolic name contains
     * "groupProperty", then throws a {@link GradleException}; otherwise simply returns.
     * <p/>
     * It will poll the server every {@link SlingServerConfiguration#getRetryWaitMs() retryWaitMs} up
     * to {@link SlingServerConfiguration # # getMaxWaitMs ( ) maxWaitMs} to
     * see if the state has changed to be ACTIVE before throwing an exception.
     *
     * @param groupProperty the part of the symbolic name to look for (e.g., "com.myco")
     * @param slingBundleSupport the server and connection to check
     *
     * @throws GradleException after polling, the bundles still are not ACTIVE
     */
    // TODO: This is used by the cq-package plugin; should probably be moved as a task into this plugin
    void checkActiveBundles(String groupProperty, SlingBundleSupport slingBundleSupport) throws GradleException {
        def serverConf = slingBundleSupport.serverConf

        def serverName = serverConf.name
        def bundleControlUriJson = slingBundleSupport.bundlesControlUri

        log.info "Checking for bundles status as Active on ${serverName} for ${groupProperty}"

        final pollingTxt = new DotPrinter()
        boolean bundlesActive = false

        block(
            serverConf.maxWaitMs,
            { serverConf.active && bundlesActive == false },
            {
                log.info pollingTxt.increment()

                def resp = slingBundleSupport.doGet(bundleControlUriJson)
                if (resp.code == HTTP_OK) {
                    try {
                        def json = new JsonSlurper().parseText(resp.body) as Map

                        if (areAllBundlesActive(json, groupProperty)) {
                            bundlesActive = true
                        }
                    }
                    catch (Exception exp) {
                        throw new GradleException("Could not parse \"${resp.body}\"", exp)
                    }
                }
                else if (resp.code == HTTP_CLIENT_TIMEOUT) {
                    serverConf.active = false
                }
            },
            serverConf.retryWaitMs
        )

        if (serverConf.active) {
            if (bundlesActive == false)
                throw new GradleException("Check Bundle Status FAILED: Not all bundles are ACTIVE on ${serverName}")
            else
                log.info("Bundles are ACTIVE on ${serverName}!")
        }
        else {
            // ignore since the server's not active
        }
    }

    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************


    @TypeChecked
    static class DotPrinter {
        private final StringBuilder str = new StringBuilder()


        String increment() {
            str.append('.' as char).toString()
        }
    }

    /**
     * Functional interface for {@link #uninstallAllBundles(List, SlingBundleSupport, UninstallBundlePredicate)}
     */
    static interface UninstallBundlePredicate {
        /**
         * Returns true if the symbolic name passed in should be uninstalled; otherwise false
         */
        boolean eval(String symbolicName)
    }

}
