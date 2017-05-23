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

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

/**
 * Handles reading the environment JSON file.
 * <p>
 * Sample of what the JSON looks like:
 * <pre>
 * {
 *   "testEnv" : {
 *      "authors": {
 *        "cq-auth01": "4502",
 *        "cq-auth02": "4502"
 *      },
 *      "publishers": {
 *        "cq-pub01": "4503",
 *        "cq-pub02": "4503"
 *      },
 *      "domainName": "test.myco.com",
 *      "protocol": "http",
 *      "username": "admin",
 *      "password": "admin",
 *      "clusterAuths": true,
 *      "clusterPubs": false
 *    }
 * }
 * </pre>
 *
 * Assuming the "slingServers.env.name" property (see documentation for {@link SlingServersConfiguration}) is "testEnv",
 * the above example would be translated to this {@link SlingServersConfiguration}:<pre>
 *   {
 *     retryWaitMs=1000,
 *     maxWaitValidateBundlesMs=10000,
 *     servers=[
 *       cq-pub01-4503: [
 *         name: cq-pub01-4503,
 *         protocol: http,
 *         port: 4503,
 *         machineName: cq-pub01.test.myco.com,
 *         username: admin,
 *         password: admin,
 *         active: true
 *       ],
 *       cq-pub02-4503: [
 *         name: cq-pub02-4503,
 *         protocol: http,
 *         port: 4503,
 *         machineName: cq-pub02.test.myco.com,
 *         username: admin,
 *         password: admin,
 *         active: true
 *       ],
 *       cq-auth01-4502: [
 *         name: cq-auth01-4502,
 *         protocol: http,
 *         port: 4502,
 *         machineName: cq-auth01.test.myco.com,
 *         username: admin,
 *         password: admin,
 *         active: true
 *       ]
 *     ]
 *   }
 * </pre>
 *
 * As shown in the example, if "clusterAuths" is true, only the first server in the "authors" section is used. The
 * same would be true of the publishers if "clusterPubs" were true.
 */
@Slf4j
@CompileStatic
class EnvironmentFileReader {

    static Map<String, SlingServerConfiguration> getServersFromFile(String fileName, String envName) {
        Map environment = getEnvironmentFromFile(fileName, envName)
        if (!environment) {
            log.warn "Could not find \"${envName}\" in \"${fileName}\""
            return [:]
        }

        def authors = environment.authors as Map
        def publishers = environment.publishers as Map
        def clusterAuths = environment.clusterAuths as boolean
        def clusterPubs = environment.clusterPubs as boolean

        if (authors) {
            if (clusterAuths)
                return clusteredAuthors(environment, authors, publishers, clusterPubs)
            else
                return unclusteredAuthors(environment, publishers, authors, clusterPubs)
        }
        else {
            if (!publishers) throw new GradleException("There are no authors or publishers defined in ${fileName}")

            return noAuthors(environment, clusterPubs, publishers)
        }
    }


    private static Map<String, SlingServerConfiguration> noAuthors(Map environment,
                                                                   boolean clusterPubs,
                                                                   Map publishers) {
        if (clusterPubs) { // clustered pubs, no auths
            def firstPubKey = publishers.keySet().first()
            def firstPub = [(firstPubKey): "${publishers[firstPubKey]}"]

            return fileMapToServerConfMap(environment, firstPub)
        }
        else { // unclustered pubs, no auths
            return fileMapToServerConfMap(environment, publishers)
        }
    }


    private static Map<String, SlingServerConfiguration> unclusteredAuthors(Map environment,
                                                                            Map publishers,
                                                                            Map authors,
                                                                            boolean clusterPubs) {
        if (publishers) {
            if (clusterPubs) { // clustered pubs, unclustered auths
                def firstPubKey = publishers.keySet().first()
                def firstPub = [(firstPubKey): "${publishers[firstPubKey]}"]

                def pubInstance = fileMapToServerConfMap(environment, firstPub)
                return fileMapToServerConfMap(environment, authors) + pubInstance
            }
            else { // unclustered pubs, unclustered auths
                return fileMapToServerConfMap(environment, publishers) +
                    fileMapToServerConfMap(environment, authors)
            }
        }
        else { // no pubs, unclustered auths
            return fileMapToServerConfMap(environment, authors)
        }
    }


    private static Map<String, SlingServerConfiguration> clusteredAuthors(Map environment,
                                                                          Map authors,
                                                                          Map publishers,
                                                                          boolean clusterPubs) {
        def firstAuthorKey = authors.keySet().first()
        def firstAuthor = [(firstAuthorKey): "${authors[firstAuthorKey]}"]

        def authInstance = fileMapToServerConfMap(environment, firstAuthor)
        if (publishers) {
            if (clusterPubs) { // clustered pubs, clustered auths
                def firstPubKey = publishers.keySet().first()
                def firstPub = [(firstPubKey): "${publishers[firstPubKey]}"]

                def pubInstance = fileMapToServerConfMap(environment, firstPub)
                return authInstance + pubInstance
            }
            else { // unclustered pubs, clustered auths
                return fileMapToServerConfMap(environment, publishers) + authInstance
            }
        }
        else { // no pubs, clustered auths
            return authInstance
        }
    }


    private static Map getEnvironmentFromFile(String filename, String envName) {
        File file = new File(filename)

        def jsonEnvironments = new JsonSlurper().parseText(file.getText()) as Map

        return jsonEnvironments.get(envName) as Map
    }


    private static Map<String, SlingServerConfiguration> fileMapToServerConfMap(Map environment, Map hostAndPort) {
        return hostAndPort.collectEntries { hostname, port ->
            return ["${hostname}-${port}":
                        new SlingServerConfiguration(
                            name: "${hostname}-${port}", protocol: (String)environment.protocol, port: ((String)port).toInteger(),
                            machineName: "${hostname}.${environment.domainName}", username: (String)environment.username,
                            password: (String)environment.password
                        )] as Map<String, SlingServerConfiguration>
        } as Map<String, SlingServerConfiguration>
    }

}
