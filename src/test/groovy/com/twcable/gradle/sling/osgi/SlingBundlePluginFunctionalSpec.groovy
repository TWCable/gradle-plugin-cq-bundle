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

import com.twcable.gradle.sling.SlingBundleFixture
import com.twcable.gradle.sling.SlingServerFixture
import groovy.transform.Immutable
import groovy.transform.TypeChecked
import nebula.test.IntegrationSpec
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.util.MultiPartInputStreamParser
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.AutoCleanup

import javax.servlet.MultipartConfigElement
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static com.twcable.gradle.sling.osgi.BundleServerConfiguration.BUNDLE_CONTROL_BASE_PATH
import static com.twcable.gradle.sling.osgi.BundleState.ACTIVE
import static com.twcable.gradle.sling.osgi.BundleState.INSTALLED
import static com.twcable.gradle.sling.osgi.BundleState.RESOLVED
import static com.twcable.gradle.sling.osgi.BundleState.UNINSTALLED

@SuppressWarnings("GroovyAssignabilityCheck")
class SlingBundlePluginFunctionalSpec extends IntegrationSpec {

    @AutoCleanup("stop")
    Server server

    HandlerCollection handlerCollection

    String projVersion = "12.3"

    GetHandler getHandler = new GetHandler()
    PostHandler postHandler = new PostHandler()

    Project fixtureProject
    SlingBundleConfiguration bundleConfig


    def setup() {
        logLevel = LogLevel.DEBUG

        fixtureProject = ProjectBuilder.builder().build()
        fixtureProject.plugins.apply JavaPlugin

        bundleConfig = fixtureProject.extensions.create(SlingProjectBundleConfiguration.NAME, SlingProjectBundleConfiguration, fixtureProject)
        bundleConfig.symbolicName = 'com.test.bundle'
        bundleConfig.installPath = '/apps/test/install'
        bundleConfig.sourceFile = new File(projectDir, "build/libs/${moduleName}-${projVersion}.jar")

        createAndStartServer()
    }


    def "remove bundle"() {
        getHandler.addPathResponse(bundlesInfoPath, activeBundlesJson)
        getHandler.addPathResponse(bundleControlPath, installedBundleJson)
        postHandler.addPathAndParamResponse(bundleControlPath, [action: 'stop'], "{\"fragment\":false,\"stateRaw\":${RESOLVED.stateRaw}}")
        postHandler.addPathAndParamResponse(bundleControlPath, [action: 'uninstall'], "{\"fragment\":false,\"stateRaw\":${UNINSTALLED.stateRaw}}")
        postHandler.addPathAndParamResponse(bundlePath, [":operation": "delete"], installedBundleDeleteResponse)

        buildFile << """
            apply plugin: 'java'
            ${applyPlugin(SlingBundlePlugin)}
            version = '${projVersion}'
            bundle {
                symbolicName = 'com.test.bundle'
                installPath = "/apps/test/install"
            }
            slingServers.publisher.active = false
        """.stripIndent()

        def res = this.launcher("-Pslingserver.author.port=${serverPort}", "removeBundle").run()

        expect:
        println res.standardOutput
        println res.standardError
        res.success
        res.wasExecuted(':stopBundle')
    }


    def "upload bundle"() {
        getHandler.addPathResponse(bundlesInfoPath, activeBundlesJson)
        getHandler.addPathResponse(bundleControlPath, installedBundleJson)

        postHandler.addPathAndParamResponse(bundleConfig.installPath, [:], newPathResponse) // make sure path is there
        postHandler.addFileResponse(bundleConfig.installPath, uploadFileResponse)
        postHandler.addPathAndParamResponse(bundleControlPath, [action: 'stop'], "{\"fragment\":false,\"stateRaw\":${RESOLVED.stateRaw}}")
        postHandler.addPathAndParamResponse(bundleControlPath, [action: 'uninstall'], "{\"fragment\":false,\"stateRaw\":${UNINSTALLED.stateRaw}}")
        postHandler.addPathAndParamResponse(bundlePath, [":operation": "delete"], installedBundleDeleteResponse)

        writeHelloWorld('com.twcable.test', projectDir)

        buildFile << """
            apply plugin: 'java'
            ${applyPlugin(SlingBundlePlugin)}
            version = '${projVersion}'
            bundle {
                symbolicName = 'com.test.bundle'
                installPath = "/apps/test/install"
            }
            slingServers.publisher.active = false
        """.stripIndent()

        def res = this.launcher("-Pslingserver.author.port=${serverPort}", "uploadBundle").run()

        expect:
        println res.standardOutput
        println res.standardError
        res.success
        res.wasExecuted(':stopBundle')
    }

    // **********************************************************************
    //
    // HELPER METHODS
    //
    // **********************************************************************


    @TypeChecked
    public String getUploadFileResponse() {
        return installedBundleFixture.uploadFileResponse()
    }


    @TypeChecked
    protected void writeHelloWorld(String packageDotted, File baseDir) {
        def path = 'src/main/java/' + packageDotted.replace('.', '/') + '/HelloWorld.java'
        def javaFile = createFile(path, baseDir)
        javaFile << """
            package ${packageDotted};

            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello Integration Test");
                }
            }
        """.stripIndent()
    }


    @TypeChecked
    void createAndStartServer() {
        server = new Server(0)
        handlerCollection = new HandlerCollection()

        // needed for multipart form POST parsing to work
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, new MultipartConfigElement(projectDir.absolutePath))
            }
        })
        addGetHandler(getHandler)
        addPostHandler(postHandler)

        server.setHandler(handlerCollection)

        server.start()
    }


    @TypeChecked
    public String getNewPathResponse() {
        return installedBundleFixture.createNewPathResponse(bundleConfig.installPath)
    }


    @TypeChecked
    String getInstalledBundleDeleteResponse() {
        return installedBundleFixture.deleteFileResponse()
    }


    @TypeChecked
    String getInstalledBundleJson() {
        return installedBundleFixture.bundleInformationJson()
    }


    @TypeChecked
    String getActiveBundlesJson() {
        return activeBundlesServerFixture.bundlesInformationJson(false)
    }


    @TypeChecked
    SlingServerFixture getActiveBundlesServerFixture() {
        return new SlingServerFixture(bundles: [getActiveBundleFixture()])
    }


    @TypeChecked
    SlingBundleFixture getActiveBundleFixture() {
        return new SlingBundleFixture(bundleConfiguration: bundleConfig, bundleState: ACTIVE)
    }


    @TypeChecked
    SlingBundleFixture getInstalledBundleFixture() {
        return new SlingBundleFixture(bundleConfiguration: bundleConfig, bundleState: INSTALLED)
    }


    @TypeChecked
    String getBundleControlPath() {
        return "${BUNDLE_CONTROL_BASE_PATH}/${bundleSymbolicName}.json".toString()
    }


    @TypeChecked
    String getBundlesInfoPath() {
        return "${BUNDLE_CONTROL_BASE_PATH}.json".toString()
    }


    @TypeChecked
    String getBundleSymbolicName() {
        return bundleConfig.symbolicName
    }


    @TypeChecked
    String getBundlePath() {
        return bundleConfig.bundlePath
    }


    @TypeChecked
    File getBundleFile() {
        return bundleConfig.sourceFile
    }


    @TypeChecked
    int getServerPort() {
        return ((ServerConnector)server.connectors[0]).localPort
    }


    @TypeChecked
    void addGetHandler(SimpleHandler handler) {
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (request.method == "GET") {
                    handler.handle(request, response)
                    baseRequest.handled = true
                }
            }
        })
    }


    @TypeChecked
    void addPostHandler(SimpleHandler handler) {
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (request.method == "POST") {
                    handler.handle(request, response)
                    baseRequest.handled = true
                }
            }
        })
    }


    @TypeChecked
    void addDeleteHandler(SimpleHandler handler) {
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (request.method == "DELETE") {
                    handler.handle(request, response)
                    baseRequest.handled = true
                }
            }
        })
    }


    @TypeChecked
    static boolean hasFile(HttpServletRequest request) {
        // make sure the request is fully parsed
        request.getParameterMap()

        def attribute = request.getAttribute("org.eclipse.jetty.multiPartInputStream") as MultiPartInputStreamParser
        return attribute.parts.find { it.submittedFileName != null } != null
    }

    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************


    static interface SimpleHandler {
        void handle(HttpServletRequest request, HttpServletResponse response)
    }


    @TypeChecked
    static class GetHandler implements SimpleHandler {
        Map<String, String> pathToResponse = [:]


        void addPathResponse(String path, String response) {
            pathToResponse.put(path, response)
        }


        @Override
        void handle(HttpServletRequest request, HttpServletResponse response) {
            def respStr = pathToResponse.find { it.key == request.pathInfo }?.value
            if (respStr != null) {
                response.writer.println(respStr)
                return
            }
            response.status = 404
        }
    }


    @TypeChecked
    static class PostHandler implements SimpleHandler {
        Map<RequestPredicate, String> predToResponse = [:]


        void addPathAndParamResponse(String path, Map params, String resp) {
            predToResponse.put(new PathAndParamPredicate(path: path, params: params), resp)
        }


        void addFileResponse(String path, String resp) {
            predToResponse.put(new PathAndFilePredicate(path), resp)
        }


        @Override
        void handle(HttpServletRequest request, HttpServletResponse response) {
            def respStr = predToResponse.find { it.key.eval(request) }?.value
            if (respStr != null) {
                response.writer.println(respStr)
                return
            }
            response.status = 404
        }
    }


    static interface RequestPredicate {
        boolean eval(HttpServletRequest request)
    }


    @Immutable
    @TypeChecked
    static class PathAndParamPredicate implements RequestPredicate {
        String path
        Map params


        boolean eval(HttpServletRequest request) {
            if (hasFile(request)) return false // ignore file streams

            if (request.pathInfo == path) {
                return params.every { request.getParameter(it.key as String) == it.value }
            }
            return false
        }
    }


    @Immutable
    @TypeChecked
    static class PathAndFilePredicate implements RequestPredicate {
        String path


        boolean eval(HttpServletRequest request) {
            return request.pathInfo == path && hasFile(request)
        }
    }

}
