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
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingSupport
import com.twcable.gradle.sling.SlingSupportFactory
import groovy.transform.TypeChecked

import javax.annotation.Nonnull

/**
 * Brings together a {@link SlingBundleConfiguration}, {@link BundleServerConfiguration} and {@link SlingSupport}
 */
@TypeChecked
@SuppressWarnings("GrFinalVariableAccess")
class SlingBundleSupport {
    final SlingBundleConfiguration bundleConfiguration
    final BundleServerConfiguration bundleServerConfiguration
    final SlingSupport slingSupport


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

    /**
     * Returns the base URL to control a bundle.
     */
    @Nonnull
    URI getBundleControlBaseUri() {
        return bundleServerConfiguration.bundleControlBaseUri
    }

    /**
     * Returns the URL to use to do actions on bundles.
     */
    @Nonnull
    URI getBundlesControlUri() {
        URI base = serverConf.baseUri
        new URI(base.scheme, base.userInfo, base.host, base.port, "${BundleServerConfiguration.BUNDLE_CONTROL_BASE_PATH}.json", null, null)
    }

}
