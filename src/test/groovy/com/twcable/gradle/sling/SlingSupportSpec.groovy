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

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import org.apache.http.client.methods.HttpPost
import spock.lang.Specification
import spock.lang.Subject

@Subject(SlingSupport)
class SlingSupportSpec extends Specification {

    @SuppressWarnings("GroovyAssignabilityCheck")
    SlingServerFixture sf = new SlingServerFixture()


    def "make_path"() {
        given:
        final httpClient = Mock(SimpleHttpClient) {
            1 * execute(_ as HttpPost) >> { new HttpResponse(201, "") }
        }

        def slingSupport = new SlingSupport(sf.slingServerConfiguration) {
            @Override
            SimpleHttpClient createHttpClient() {
                return httpClient
            }
        }

        expect:
        slingSupport.makePath(URI.create("http://test/fooble"))
    }

}
