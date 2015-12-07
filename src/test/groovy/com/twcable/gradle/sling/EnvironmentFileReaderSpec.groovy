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
package com.twcable.gradle.sling

import nebula.test.ProjectSpec
import org.gradle.api.GradleException
import org.junit.After
import spock.lang.Subject

import static com.twcable.gradle.sling.SlingServersConfiguration.ENV_FILE_SYSTEM_PROPERTY
import static com.twcable.gradle.sling.SlingServersConfiguration.ENV_NAME_SYSTEM_PROPERTY

@Subject(EnvironmentFileReader)
@SuppressWarnings("GroovyPointlessBoolean")
class EnvironmentFileReaderSpec extends ProjectSpec {

    @After
    public void clearProperties() {
        System.clearProperty(ENV_FILE_SYSTEM_PROPERTY);
        System.clearProperty(ENV_NAME_SYSTEM_PROPERTY);
    }


    def "can parse envFile for expected servers"() {
        given:
        System.setProperty(ENV_FILE_SYSTEM_PROPERTY, createenvFileFile().absolutePath)
        System.setProperty(ENV_NAME_SYSTEM_PROPERTY, "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        println "servers: ${servers}"

        expect:
        servers.collect { it.name }.sort().unique(false) == ["cq-auth01-4502",
                                                             "cq-pub01-4503",
                                                             "cq-pub02-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["cq-auth01.test.myco.com",
                                                                    "cq-pub01.test.myco.com",
                                                                    "cq-pub02.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse no cluster envFile for expected servers"() {
        given:
        System.setProperty(ENV_FILE_SYSTEM_PROPERTY, createNoClusterenvFileFile().absolutePath)
        System.setProperty(ENV_NAME_SYSTEM_PROPERTY, "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        expect:
        servers.collect { it.name }.sort().unique(false) == ["cq-auth01-4502",
                                                             "cq-auth02-4502",
                                                             "cq-pub01-4503",
                                                             "cq-pub02-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["cq-auth01.test.myco.com",
                                                                    "cq-auth02.test.myco.com",
                                                                    "cq-pub01.test.myco.com",
                                                                    "cq-pub02.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse cluster all envFile for expected servers"() {
        given:
        System.setProperty(ENV_FILE_SYSTEM_PROPERTY, createClusterAllenvFileFile().absolutePath)
        System.setProperty(ENV_NAME_SYSTEM_PROPERTY, "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        expect:
        servers.collect { it.name }.sort().unique(false) == ["cq-auth01-4502",
                                                             "cq-pub01-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["cq-auth01.test.myco.com",
                                                                    "cq-pub01.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse cluster pubs envFile for expected servers"() {
        given:
        System.setProperty(ENV_FILE_SYSTEM_PROPERTY, createClusterPubsEnvFile().absolutePath)
        System.setProperty(ENV_NAME_SYSTEM_PROPERTY, "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        expect:
        servers.collect { it.name }.sort().unique(false) == ["cq-auth01-4502",
                                                             "cq-auth02-4502",
                                                             "cq-pub01-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["cq-auth01.test.myco.com",
                                                                    "cq-auth02.test.myco.com",
                                                                    "cq-pub01.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse same hostname envFile for expected servers"() {
        given:
        System.setProperty(ENV_FILE_SYSTEM_PROPERTY, createSameHostnameEnvFile().absolutePath)
        System.setProperty(ENV_NAME_SYSTEM_PROPERTY, "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        expect:
        servers.collect { it.name }.sort().unique(false) == ["localhost-4502",
                                                             "localhost-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["localhost.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse no-auth envFile for expected servers"() {
        given:
        System.setProperty(ENV_FILE_SYSTEM_PROPERTY, createNoAuthEnvFile().absolutePath)
        System.setProperty(ENV_NAME_SYSTEM_PROPERTY, "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        expect:
        servers.collect { it.name }.containsAll(["cq-pub01-4503", "cq-pub02-4503"])
        servers.collect { it.protocol }.contains("http")
        servers.collect { it.port }.containsAll([4503])
        servers.collect { it.machineName }.containsAll(["cq-pub01.test.myco.com",
                                                        "cq-pub02.test.myco.com"])
        servers.collect { it.username }.contains("admin")
        servers.collect { it.password }.contains("admin")
    }


    def "can parse no-pub envFile for expected servers"() {
        given:
        System.setProperty(ENV_FILE_SYSTEM_PROPERTY, createNoPubEnvFile().absolutePath)
        System.setProperty(ENV_NAME_SYSTEM_PROPERTY, "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration(project)

        expect:
        servers.collect { it.name }.containsAll(["cq-auth01-4502"])
        servers.collect { it.protocol }.contains("http")
        servers.collect { it.port }.containsAll([4502])
        servers.collect { it.machineName }.containsAll(["cq-auth01.test.myco.com"])
        servers.collect { it.username }.contains("admin")
        servers.collect { it.password }.contains("admin")
    }


    @SuppressWarnings("GroovyResultOfObjectAllocationIgnored")
    def "can parse no-auth and no-pub envFile for expected servers"() {
        given:
        System.setProperty(ENV_FILE_SYSTEM_PROPERTY, createNoAuthNoPubEnvFile().absolutePath)
        System.setProperty(ENV_NAME_SYSTEM_PROPERTY, "testEnv")

        when:
        new SlingServersConfiguration(project)

        then:
        true // some weird compile bug makes this required
        thrown(GradleException)
    }


    static File createenvFileFile() {
        File envFile = File.createTempFile('envFile', '.json')
        envFile.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envFile
    }


    static File createNoClusterenvFileFile() {
        File envFile = File.createTempFile('envFile', '.json')
        envFile.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": false,
                "clusterPubs": false
              }
            }
            ''')
        return envFile
    }


    static File createClusterAllenvFileFile() {
        File envFile = File.createTempFile('envFile', '.json')
        envFile.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": true
              }
            }
            ''')
        return envFile
    }


    static File createClusterPubsEnvFile() {
        File envFile = File.createTempFile('envFile', '.json')
        envFile.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": false,
                "clusterPubs": true
              }
            }
            ''')
        return envFile
    }


    static File createSameHostnameEnvFile() {
        File envFile = File.createTempFile('envFile', '.json')
        envFile.write('''
            {
              "testEnv" : {
                "authors": {
                  "localhost": "4502"
                },
                "publishers": {
                  "localhost": "4503"
                },
                "dispatchers": [
                  "localhost"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envFile
    }


    static File createNoPubEnvFile() {
        File envFile = File.createTempFile('envFile', '.json')
        envFile.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envFile
    }


    static File createNoAuthEnvFile() {
        File envFile = File.createTempFile('envFile', '.json')
        envFile.write('''
            {
              "testEnv" : {
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envFile
    }


    static File createNoAuthNoPubEnvFile() {
        File envFile = File.createTempFile('envFile', '.json')
        envFile.write('''
            {
              "testEnv" : {
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envFile
    }

}
