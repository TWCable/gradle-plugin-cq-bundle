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
import org.gradle.api.Project
import org.gradle.api.internal.plugins.osgi.OsgiHelper
import org.gradle.api.tasks.bundling.AbstractArchiveTask

import javax.annotation.Nonnull

/**
 * Convention settings to describe an OSGi bundle.
 *
 * Follows to patterns used for a Gradle "named domain object".
 */
@Slf4j
@TypeChecked
class SlingBundleConfiguration {
    /**
     * The name to register the configuration under in the project's extensions
     */
    public static final String NAME = 'bundle'

    /**
     * The path to install the bundle under if none is explicitly provided
     */
    public static final String DEFAULT_INSTALL_PATH = '/apps/install'

    String name

    private String _symbolicName

    // TODO: Guard against trailing slash
    String installPath = DEFAULT_INSTALL_PATH

    private File _sourceFile

    @SuppressWarnings("GrFinalVariableAccess")
    final Project project

    // TODO: Remove the Project dependency
    SlingBundleConfiguration(@Nonnull Project project) {
        if (project == null) throw new IllegalArgumentException("project == null")
        this.project = project
    }


    @Override
    String toString() {
        return "SlingBundleConfiguration(${symbolicName} for project ${project.path})"
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
            if (project.tasks.findByName('jar') == null) throw new IllegalStateException("There is not a 'jar' task for ${project}")
            // don't cache this lookup in case task's path changes later in the lifecycle
            return (project.tasks.getByName('jar') as AbstractArchiveTask).archivePath
        }
        return _sourceFile
    }


    void setSymbolicName(String symbolicName) {
        this._symbolicName = symbolicName
    }

    /**
     * Returns the bundle's symbolic name. If not explicitly set, derives it using
     * {@link OsgiHelper#getBundleSymbolicName(Project)}
     */
    @Nonnull
    String getSymbolicName() {
        if (_symbolicName == null) {
            def osgiHelper = new OsgiHelper()
            _symbolicName = osgiHelper.getBundleSymbolicName(project)
        }
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
