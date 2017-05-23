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
package com.twcable.gradle.sling

import com.twcable.gradle.sling.osgi.BundleState
import com.twcable.gradle.sling.osgi.SlingBundleConfiguration
import groovy.json.JsonBuilder
import groovy.transform.Immutable
import groovy.transform.TypeChecked

import javax.annotation.Nonnull

import static com.twcable.gradle.sling.osgi.BundleState.ACTIVE
import static com.twcable.gradle.sling.osgi.BundleServerConfiguration.BUNDLE_CONTROL_BASE_PATH
import static java.net.HttpURLConnection.HTTP_CREATED
import static java.net.HttpURLConnection.HTTP_FORBIDDEN
import static java.net.HttpURLConnection.HTTP_OK

/**
 * Class for creating JSON responses for testing like would come from a "real" Felix webconsole servlet.
 * <p/>
 * Based on
 * https://github.com/apache/felix/blob/fabc5d/webconsole/src/main/java/org/apache/felix/webconsole/internal/core/BundlesServlet.java
 */
@TypeChecked
@Immutable(knownImmutableClasses = [SlingBundleConfiguration])
@SuppressWarnings(["GroovyUnusedDeclaration"])
class SlingBundleFixture {
    SlingBundleConfiguration bundleConfiguration
    BundleState bundleState = ACTIVE
    int felixId = 284

    /**
     * Returns the JSON with full information for this specific bundle
     */
    @Nonnull
    String bundleInformationJson() {
        return new SlingServerFixture(bundles: [this]).bundlesInformationJson(true)
    }

    /**
     * A map of the bundle data (including "props" from {@link #bundleProperties()})
     */
    @Nonnull
    Map bundleData() {
        return [props       : bundleProperties(),
                id          : felixId,
                name        : bundleConfiguration.name,
                fragment    : false,
                stateRaw    : bundleState.stateRaw,
                state       : bundleState.stateString,
                version     : bundleConfiguration.version,
                symbolicName: bundleConfiguration.symbolicName,
                category    : ""
        ]
    }

    /**
     * A single-item list that contains maps of key/value pairs for the "props" in the Bundle information JSON
     */
    @Nonnull
    List<Map<String, Object>> bundleProperties() {
        def version = bundleConfiguration.version
        def description = bundleConfiguration.symbolicName
        def symbolicName = bundleConfiguration.symbolicName
        def packageName = symbolicName.split(/\./).reverse().join('.')

        return [
            [key: "Symbolic Name", value: symbolicName],
            [key: "Version", value: version],
            [key: "Bundle Location", value: "jcrinstall:${bundleConfiguration.bundlePath}"],
            [key: "Last Modification", value: "Wed Jan 09 10:59:24 MST 2013"],
            [key: "Vendor", value: "Time Warner Cable"],
            [key: "Description", value: description],
            [key: "Start Level", value: 20],
            [key: "Exported Packages", value: ["${packageName},version=${version}"]],

            // TODO: Add Services

            [key  : "Imported Packages",
             value: ["javax.jcr,version=2.0.0 from <a href='${BUNDLE_CONTROL_BASE_PATH}/55'>javax.jcr (55)</a>",
                     "org.slf4j,version=1.6.4 from <a href='${BUNDLE_CONTROL_BASE_PATH}/11'>slf4j.api (11)</a>"]],
            [key  : "Importing Bundles",
             value: ["<a href='${BUNDLE_CONTROL_BASE_PATH}/239'>com.test.servlets (239)</a>",
                     "<a href='${BUNDLE_CONTROL_BASE_PATH}/277'>com.test.servlets (277)</a>"]],
            [key  : "Manifest Headers",
             value: ["Bundle-Description: ${description}",
                     "Bundle-ManifestVersion: 2",
                     "Bundle-Name: ${bundleConfiguration.name}",
                     "Bundle-SymbolicName: ${symbolicName}",
                     "Bundle-Vendor: Time Warner Cable",
                     "Bundle-Version: ${version}",
                     "Export-Package: ${packageName}; uses:=\\\"javax.jcr, org.slf4j\\\"; version=\\\"${version}\\\"",
                     "Implementation-Title: Unit Testing Bundle",
                     "Implementation-Version: ${version}",
                     "Import-Package: javax.jcr; version=\\\"[2.0, 3)\\\", org.slf4j; version=\\\"[1.6, 2)\\\"",
                     "Manifest-Version: 1.0"]]
        ] as List<Map<String, Object>>
    }


    String uploadFileResponse() {
        return uploadTheFileResponse(bundleConfiguration.bundlePath)
    }


    static String uploadTheFileResponse(String bundlePath) {
        final installLocation = new File(bundlePath).parent
        return new JsonBuilder([
            changes         : [
                [type: 'created', argument: bundlePath],
                [type: 'created', argument: "${bundlePath}/jcr:content"],
                [type: 'modified', argument: "${bundlePath}/jcr:content/jcr:lastModified"],
                [type: 'modified', argument: "${bundlePath}/jcr:content/jcr:mimeType"],
                [type: 'modified', argument: "${bundlePath}/jcr:content/jcr:data"],
                [type: 'modified', argument: "${installLocation}/_noredir_"],
            ],
            path            : installLocation,
            location        : installLocation,
            parentLocation  : new File(installLocation).parent,
            'status.code'   : HTTP_OK,
            'status.message': 'OK',
            title           : "Content modified ${installLocation}",
            referer         : ''
        ]).toPrettyString()
    }

    /**
     * Returns SlingPostServlet's JSON response when POSTing with no arguments to a path that does not exist
     */
    @Nonnull
    static String createNewPathResponse(String path) {
        if (path == null) throw new IllegalArgumentException("path == null")
        return new JsonBuilder([
            changes         : [
                // if there are multiple paths that need to be created (i.e., for parents that did not exist)
                // then they would be included as individual entries
                [type: 'created', argument: path],
            ],
            path            : path,
            location        : path,
            parentLocation  : new File(path).parent,
            isCreate        : true,
            'status.code'   : HTTP_CREATED,
            'status.message': 'Created',
            title           : "Content created ${path}",
            referer         : ''
        ]).toString()
    }

    /**
     * Returns SlingPostServlet's JSON response when POSTing with no arguments to a path that exists
     */
    @Nonnull
    static String createExistingPathResponse(String path) {
        return new JsonBuilder([
            changes         : [],
            path            : path,
            location        : path,
            parentLocation  : new File(path).parent,
            'status.code'   : HTTP_OK,
            'status.message': 'OK',
            title           : "Content modified ${path}",
            referer         : ''
        ]).toPrettyString()
    }

    /**
     * Returns SlingPostServlet's JSON response when POSTing with [ :operation = delete] to a path that exists
     */
    String deleteFileResponse() {
        return new JsonBuilder([
            changes         : [
                [type: 'deleted', argument: bundleConfiguration.installPath],
            ],
            path            : bundleConfiguration.installPath,
            location        : bundleConfiguration.installPath,
            parentLocation  : new File(bundleConfiguration.installPath).parent,
            'status.code'   : HTTP_OK,
            'status.message': 'OK',
            title           : "Content modified ${bundleConfiguration.installPath}",
            referer         : ''
        ]).toPrettyString()
    }

    /**
     * Returns SlingPostServlet's JSON response when POSTing with [ :operation = delete] to a path that does not exist
     */
    String deleteBadFileResponse() {
        return deleteBadFileResponse(bundleConfiguration.installPath)
    }

    /**
     * Returns SlingPostServlet's JSON response when POSTing with [ :operation = delete] to a path that does not exist
     */
    static String deleteBadFileResponse(String installLocation) {
        return new JsonBuilder([
            changes         : [],
            path            : installLocation,
            location        : installLocation,
            parentLocation  : new File(installLocation).parent,
            'status.code'   : HTTP_FORBIDDEN,
            'status.message': 'OK',
            title           : "DeleteOperation request cannot include any selectors, extension or suffix",
            referer         : ''
        ]).toPrettyString()
    }

}
