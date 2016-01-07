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
import groovy.util.logging.Slf4j

import javax.annotation.Nonnull

/**
 * Description of an OSGi bundle.
 */
@Slf4j
@TypeChecked
class SlingBundleConfiguration {

    /**
     * The path to install the bundle under if none is explicitly provided
     */
    public static final String DEFAULT_INSTALL_PATH = '/apps/install'

    String name

    protected String _symbolicName
    protected String _version

    // TODO: Guard against trailing slash
    String installPath = DEFAULT_INSTALL_PATH

    protected File _sourceFile


    protected SlingBundleConfiguration() {
        // subclasses using this constructor should be careful to set 'symbolicName' and 'version'
    }


    @SuppressWarnings("GroovyUnusedDeclaration")
    SlingBundleConfiguration(@Nonnull String symbolicName, @Nonnull String version) {
        if (symbolicName == null) throw new IllegalArgumentException("symbolicName == null")
        if (version == null) throw new IllegalArgumentException("symbolicName == null")
        this._symbolicName = symbolicName
    }


    @Override
    String toString() {
        return "SlingBundleConfiguration(${symbolicName})"
    }


    void setSourceFile(File file) {
        _sourceFile = file
    }

    /**
     * Returns the local File for the bundle. If not explicitly set, it defaults to the 'archivePath' of
     * the 'jar' task.
     */
    @Nonnull
    File getSourceFile() {
        if (_sourceFile == null) {
            throw new IllegalStateException("Source file has not been set for ${this}")
        }
        return _sourceFile
    }


    void setVersion(String version) {
        this._version = version
    }


    String getVersion() {
        return _version
    }


    void setSymbolicName(String symbolicName) {
        this._symbolicName = symbolicName
    }

    /**
     * Returns the bundle's symbolic name.
     */
    @Nonnull
    String getSymbolicName() {
        return _symbolicName
    }


    @Nonnull
    String getBundlePath() {
        return "${installPath}/${sourceFile.name}"
    }

    /**
     * The bundle control URI using the server configuration
     */
    @Nonnull
    URI getBundleUrl(@Nonnull BundleServerConfiguration bundleServerConfiguration) {
        if (bundleServerConfiguration == null) throw new IllegalArgumentException("bundleServerConfiguration == null")
        return getTheBundleUrl(symbolicName, bundleServerConfiguration)
    }

    /**
     * The bundle control URI using the server configuration
     */
    @Nonnull
    static URI getTheBundleUrl(String symbolicName, BundleServerConfiguration bundleServerConfiguration) {
        def baseUri = bundleServerConfiguration.bundleControlBaseUri
        return new URI(baseUri.scheme, baseUri.userInfo, baseUri.host,
            baseUri.port, "${baseUri.path}/${symbolicName}.json", null, null).normalize()
    }

    /**
     * The bundle installation URI using the server configuration (the parent path, not including the file name)
     */
    @Nonnull
    URI getBundleInstallUrl(@Nonnull SlingServerConfiguration serverConfiguration) {
        return getTheBundleInstallUrl(installPath, serverConfiguration)
    }

    /**
     * The bundle installation URI using the server configuration (the parent path, not including the file name)
     */
    @Nonnull
    static URI getTheBundleInstallUrl(String installPath, SlingServerConfiguration serverConfiguration) {
        def baseUri = serverConfiguration.getBaseUri()
        return new URI(baseUri.scheme, baseUri.userInfo, baseUri.host,
            baseUri.port, "${baseUri.path}/${installPath}", null, null).normalize()
    }

}
