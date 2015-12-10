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

import com.twcable.gradle.http.DefaultSimpleHttpClient
import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.ContentBody
import org.apache.http.entity.mime.content.StringBody

import javax.annotation.Nonnull
import java.nio.charset.Charset

import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_CREATED
import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static java.net.HttpURLConnection.HTTP_OK

/**
 * This provides methods to easily use the HTTP interface to Sling against a specific server.
 */
@Slf4j
@TypeChecked
@SuppressWarnings(["GroovyMultipleReturnPointsPerMethod", "GrMethodMayBeStatic", "GrFinalVariableAccess"])
class SlingSupport {
    private static final Charset UTF8 = Charset.forName('UTF-8')

    private final SlingServerConfiguration _serverConf

    private SimpleHttpClient _httpClient

    /**
     * Create an instance that is bound to interactions with the given server
     */
    SlingSupport(@Nonnull SlingServerConfiguration slingServerConfiguration) {
        if (slingServerConfiguration == null) throw new IllegalArgumentException("slingServerConfiguration == null")
        this._serverConf = slingServerConfiguration
    }

    /**
     * While "predicate" is true and "maxWaitTime" hasn't been surpassed, will run "action" every
     * "waitPeriodMs" milliseconds
     *
     * @param maxWaitTimeMs max number of milliseconds to run the action
     * @param predicate while true, will continue running "action"
     * @param action the action to run
     * @param waitPeriodMs how long to wait (in milliseconds) between executions of "action"
     */
    static void block(long maxWaitTimeMs, BlockPredicate predicate, Runnable action, long waitPeriodMs) {
        if (waitPeriodMs < 1) throw new IllegalArgumentException("waitPeriodMs < 1: ${waitPeriodMs}")
        log.info "Blocking for at most ${maxWaitTimeMs}ms"

        long startTime = System.currentTimeMillis()
        long stopTime = startTime + maxWaitTimeMs

        action.run()
        while (predicate.eval() && (System.currentTimeMillis() <= stopTime)) {
            log.debug "Trying again after waiting for ${waitPeriodMs}ms"
            Thread.sleep(waitPeriodMs)
            action.run()
        }
    }

    /**
     * The SimpleHttpClient to use for HTTP communication with the server
     */
    @Nonnull
    protected final SimpleHttpClient httpClient() {
        if (_httpClient == null) {
            _httpClient = createHttpClient()
        }
        return _httpClient
    }

    /**
     * Extension method to allow switching out SimpleHttpClient implementation
     */
    @Nonnull
    protected SimpleHttpClient createHttpClient() {
        return new DefaultSimpleHttpClient(username: serverConf.username, password: serverConf.password)
    }

    /**
     * Calls the given closure, passing it a new instance of {@link SimpleHttpClient}.
     * @param closure the closure to invoke
     * @return the result of calling the closure
     */
    HttpResponse doHttp(HttpClientAction action) {
        if (!serverConf.active) return new HttpResponse(HTTP_CLIENT_TIMEOUT, "${serverConf.name} is not responding")
        try {
            return action.run(httpClient())
        }
        finally {
            httpClient().shutdown()
        }
    }

    /**
     * Calls HTTP GET against the given URL.
     *
     * @param url the URL to GET
     *
     * @return HTTP_OK if it was successful;
     * HTTP_CLIENT_TIMEOUT if the server doesn't respond;
     * HTTP_NOT_FOUND if the URL is missing
     */
    @Nonnull
    HttpResponse doGet(URI url) {
        if (!serverConf.active) return new HttpResponse(HTTP_CLIENT_TIMEOUT, "${serverConf.name} is not responding")
        if (url == null) return new HttpResponse(HTTP_NOT_FOUND, 'Missing URL')
        log.info "GET ${url}"
        HttpGet get = new HttpGet(url)
        def resp = httpClient().execute(get)
        if (resp.code == HTTP_CLIENT_TIMEOUT) serverConf.active = false
        return resp
    }

    /**
     * Calls HTTP POST against the given URL.
     *
     * @param url the URL to POST to
     * @param parts a mapping of the parts of the POST
     *
     * @return HTTP_OK or HTTP_CREATED if it was successful;
     * HTTP_CLIENT_TIMEOUT if the server doesn't respond;
     * HTTP_NOT_FOUND if the URL is missing
     */
    HttpResponse doPost(URI url, Map parts) {
        if (!serverConf.active) return new HttpResponse(HTTP_CLIENT_TIMEOUT, "${serverConf.name} is not responding")
        if (url == null) return new HttpResponse(HTTP_NOT_FOUND, 'Missing URL')
        HttpPost post = new HttpPost(url)
        MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)

        parts.each { key, value ->
            final ContentBody val

            switch (value) {
                case String:
                    val = stringBody((String)value); break
                case ContentBody:
                    val = (ContentBody)value; break
                case File:
                    val = null; break
                default:
                    val = null
            }
            entity.addPart((String)key, val)
        }

        post.entity = entity

        log.info "POST ${url} - ${parts}"

        def resp = httpClient().execute(post)
        if (resp.code == HTTP_CLIENT_TIMEOUT) serverConf.active = false
        return resp
    }

    /**
     * Create the nodes needed to fully represent the given URL path. Missing intermediate nodes are created.
     *
     * @param url the Node to create
     *
     * @return was it successful?
     */
    boolean makePath(URI url) {
        if (url.path.endsWith('/')) {
            // strip the trailing slash
            url = new URI(url.toString()[0..-2])
        }
        def resp = doPost(url, Collections.EMPTY_MAP)
        return resp.code == HTTP_OK || resp.code == HTTP_CREATED
    }


    private static StringBody stringBody(String text) {
        new StringBody(text, 'text/plain', UTF8)
    }


    @Nonnull
    SlingServerConfiguration getServerConf() {
        return _serverConf
    }

    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************

    /**
     * Functional interface for {@link #block(long, BlockPredicate, Runnable, long)}
     */
    static interface BlockPredicate {
        /**
         * Returns true while some injected mutating state remains true
         * @return false when the mutating status is no longer the case
         */
        boolean eval()
    }

    /**
     * Functional interface for {@link #doHttp(HttpClientAction)}
     */
    static interface HttpClientAction {
        /**
         * Perform some action using the {@link SimpleHttpClient httpClient} and return the {@link HttpResponse}
         */
        @Nonnull
        HttpResponse run(@Nonnull SimpleHttpClient httpClient)
    }

}
