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
import com.twcable.gradle.sling.SlingSupport
import com.twcable.gradle.sling.SlingSupportFactory
import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.apache.http.entity.mime.content.FileBody

import javax.annotation.Nonnull

import static java.net.HttpURLConnection.HTTP_BAD_METHOD

/**
 * Brings together a {@link SlingBundleConfiguration}, {@link BundleServerConfiguration} and {@link SlingSupport}
 */
@Slf4j
@TypeChecked
@SuppressWarnings("GrFinalVariableAccess")
class SlingBundleSupport {
    final SlingBundleConfiguration bundleConfiguration
    final BundleServerConfiguration bundleServerConfiguration
    final SlingSupport slingSupport

    // TODO: SlingSupport contains a SlingServerConfiguration, which can be used to create a BundleServerConfiguration
    SlingBundleSupport(SlingBundleConfiguration bundleConfiguration,
                       BundleServerConfiguration bundleServerConfiguration,
                       SlingSupport slingSupport) {
        if (bundleConfiguration == null) throw new IllegalArgumentException("bundleConfiguration == null")
        if (bundleServerConfiguration == null) throw new IllegalArgumentException("bundleServerConfiguration == null")
        if (slingSupport == null) throw new IllegalArgumentException("slingSupport == null")

        this.bundleConfiguration = bundleConfiguration
        this.bundleServerConfiguration = bundleServerConfiguration
        this.slingSupport = slingSupport

        // consistency check
        if (slingSupport.serverConf != bundleServerConfiguration.serverConf)
            throw new IllegalArgumentException("${slingSupport.serverConf} != ${bundleServerConfiguration.serverConf}")
    }


    static SlingBundleSupport create(SlingBundleConfiguration bundleConfiguration,
                                     SlingServerConfiguration serverConfiguration,
                                     SlingSupportFactory slingSupportFactory) {
        def bundleServerConf = new BundleServerConfiguration(serverConfiguration)
        return create(bundleConfiguration, bundleServerConf, slingSupportFactory)
    }


    static SlingBundleSupport create(SlingBundleConfiguration bundleConfiguration,
                                     BundleServerConfiguration bundleServerConf,
                                     SlingSupportFactory slingSupportFactory) {
        def slingSupport = slingSupportFactory.create(bundleServerConf.serverConf)
        return new SlingBundleSupport(bundleConfiguration, bundleServerConf, slingSupport)
    }


    SlingServerConfiguration getServerConf() {
        return slingSupport.serverConf
    }


    HttpResponse doGet(URI uri) {
        return slingSupport.doGet(uri)
    }


    HttpResponse doPost(URI uri, Map parts) {
        return slingSupport.doPost(uri, parts)
    }


    @Nonnull
    HttpResponse stopBundle() {
        return slingSupport.doPost(bundleUrl, ['action': 'stop'])
    }


    @Nonnull
    HttpResponse startBundle() {
        return slingSupport.doPost(bundleUrl, ['action': 'start'])
    }


    @Nonnull
    HttpResponse refreshBundle() {
        return slingSupport.doPost(bundleUrl, ['action': 'refresh'])
    }


    @Nonnull
    HttpResponse updateBundle() {
        return slingSupport.doPost(bundleUrl, ['action': 'update'])
    }


    @Nonnull
    HttpResponse uninstallBundle() {
        return slingSupport.doPost(bundleUrl, ['action': 'uninstall'])
    }


    @Nonnull
    HttpResponse removeBundle(URI bundleLocation) {
        return slingSupport.doPost(bundleLocation, [':operation': 'delete'])
    }


    @Nonnull
    HttpResponse uploadBundle() {
        def serverConfiguration = slingSupport.serverConf
        if (!serverConfiguration.active) throw new IllegalArgumentException("serverConfiguration ${serverConfiguration.name} is not active")

        final installUri = bundleConfiguration.getBundleInstallUrl(serverConfiguration)
        final sourceFile = bundleConfiguration.sourceFile

        if (slingSupport.makePath(installUri)) {
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

    /**
     * The bundle control URI using the server configuration
     */
    @Nonnull
    URI getBundleUrl() {
        return SlingBundleConfiguration.getTheBundleUrl(bundleConfiguration.symbolicName, this.bundleServerConfiguration)
    }


    @Nonnull
    static HttpResponse refreshOsgiPackages(@Nonnull SlingSupport slingSupport, @Nonnull URI bundleControlUri) {
        return slingSupport.doPost(bundleControlUri, ['action': 'refreshPackages'])
    }

}
