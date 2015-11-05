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
package com.twcable.gradle.http

import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.client.methods.HttpUriRequest

interface SimpleHttpClient {
    void setParameter(String name, Object value)


    void setCredentials(AuthScope scope, Credentials credentials)


    HttpResponse execute(HttpUriRequest httpMessage)


    void shutdown()
}
