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
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import com.twcable.gradle.sling.SlingSupportFactory
import groovy.json.JsonSlurper
import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

import javax.annotation.Nonnull
import javax.annotation.Nullable

import static BundleState.RESOLVED
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
    static void startInactiveBundles(@Nonnull SlingSupport slingSupport) {
        def bundleServerConf = new BundleServerConfiguration(slingSupport.serverConf)
        def resp = slingSupport.doGet(bundleServerConf.bundlesControlUri)

        if (resp.code == HTTP_OK) {
            Map json = new JsonSlurper().parseText(resp.body) as Map
            List<Map> data = json.data as List

            def inactiveTwcBundles = data.findAll {
                it.state == RESOLVED.stateString
            }.collect { it.symbolicName } as List<String>

            inactiveTwcBundles.each { String symbolicName ->
                def slingBundleSupport = new SlingBundleSupport(new SlingBundleConfiguration(symbolicName, ""), bundleServerConf, slingSupport)
                log.info "Trying to start inactive bundle: ${symbolicName}"
                slingBundleSupport.startBundle()
            }
        }
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
                def gotBadResponse = resp == null || isBadResponse(resp.code, missingIsOk)
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
        return SlingBundleSupport.refreshOsgiPackages(slingSupport, bundleControlUri)
    }

}
