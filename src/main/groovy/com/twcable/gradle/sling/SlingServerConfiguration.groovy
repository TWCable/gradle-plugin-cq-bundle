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

import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j

import javax.annotation.Nonnull

/**
 * Holds the configuration of a particular Sling server.
 *
 * <h2>Primary Properties</h2>
 * <ul>
 *     <li>name</li>
 *     <li>protocol</li>
 *     <li>port</li>
 *     <li>machineName</li>
 *     <li>username</li>
 *     <li>password</li>
 *     <li>retryWaitMs</li>
 *     <li>maxWaitMs</li>
 *     <li>active</li>
 * </ul>
 */
@Slf4j
@TypeChecked
class SlingServerConfiguration {

    /**
     * The name to use for this server (e.g., "webcms-auth01-4502")
     */
    @Nonnull
    String name

    /**
     * The protocol for this server (http/https)
     */
    @Nonnull
    String protocol = 'http'

    /**
     * The port to connect to for this server
     */
    int port = 9999

    /**
     * The name of this server (e.g., "localhost")
     */
    @Nonnull
    String machineName = 'localhost'

    /**
     * The login to use for this server
     */
    @Nonnull
    String username = 'admin'

    /**
     * The password to use for this server
     */
    @Nonnull
    String password = 'admin'

    /**
     * When a command fails (usually because the server is busy), how many milliseconds should
     * we wait before trying again? Defaults to 1 second.
     */
    long retryWaitMs = 1000

    /**
     * When a command fails (usually because the server is busy), how many milliseconds should
     * we wait at most before giving up? Defaults to 10 seconds.
     */
    long maxWaitMs = 10000

    private boolean _active = true


    @Override
    String toString() {
        return "SlingServer(name:${name}, protocol:${protocol}, port:${port}, machineName:${machineName}, " +
            "username:${username}, password:XXXX, retryWaitMs:${retryWaitMs}, " +
            "maxWaitMs:${maxWaitMs} active:${active})"
    }

    /**
     * A URI of the protocol, machine name and port
     */
    @Nonnull
    URI getBaseUri() {
        new URI(protocol, null, machineName, port, '/', null, null)
    }

    /**
     * Is this server active (responding to requests) or not?
     */
    boolean getActive() {
        return _active
    }

    /**
     * Is this server active (responding to requests) or not?
     */
    void setActive(boolean active) {
        if (_active && !active) {
            log.warn("Marking ${name} as being inactive")
        }
        _active = active
    }

    /**
     * Sets the property to the given value, doing type coercion if needed
     */
    void setTheProperty(@Nonnull final String propName, Object value) {
        if (propName == null) throw new IllegalArgumentException("propName == null")

        String realPropName = propName

        // do any needed translations
        switch (propName) {
            case 'machinename': realPropName = 'machineName'; break
            case 'retry.ms': realPropName = 'retryWaitMs'; break
            case 'max.ms': realPropName = 'maxWaitMs'; break
        }

        if (!this.hasProperty(realPropName)) {
            throw new IllegalArgumentException("\"${realPropName}\" is not a known property on ${this}")
        }

        final propertyType = this.metaClass.getMetaProperty(realPropName).type

        switch (propertyType) {
            case Integer:
            case Integer.TYPE:
                setProperty(realPropName, Integer.valueOf(value.toString())); break
            case Long:
            case Long.TYPE:
                setProperty(realPropName, Long.valueOf(value.toString())); break
            case Boolean:
            case Boolean.TYPE:
                setProperty(realPropName, Boolean.valueOf(value.toString())); break
            default:
                setProperty(realPropName, value)
        }
    }

}
