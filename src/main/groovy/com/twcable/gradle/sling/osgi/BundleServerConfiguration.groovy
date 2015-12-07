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

import com.twcable.gradle.sling.SlingServerConfiguration
import groovy.transform.TypeChecked

import javax.annotation.Nonnull

/**
 * Enriches {@link SlingServerConfiguration} with additional information for dealing with bundles
 */
@TypeChecked
class BundleServerConfiguration {
    static final String BUNDLE_CONTROL_BASE_PATH = '/system/console/bundles'

    final SlingServerConfiguration serverConf


    BundleServerConfiguration(SlingServerConfiguration serverConfiguration) {
        this.serverConf = serverConfiguration
    }

    /**
     * Returns the base URL to control a bundle.
     */
    @Nonnull
    URI getBundleControlBaseUri() {
        URI base = serverConf.baseUri
        return new URI(base.scheme, base.userInfo, base.host, base.port, BUNDLE_CONTROL_BASE_PATH, null, null)
    }

    /**
     * Returns the URL to use to do actions on bundles.
     */
    @Nonnull
    URI getBundlesControlUri() {
        URI base = serverConf.baseUri
        return new URI(base.scheme, base.userInfo, base.host, base.port, "${BUNDLE_CONTROL_BASE_PATH}.json", null, null)
    }

}
