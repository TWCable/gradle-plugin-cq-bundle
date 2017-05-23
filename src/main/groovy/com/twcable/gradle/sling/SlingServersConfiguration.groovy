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

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException
import org.gradle.api.Project

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.regex.Pattern

/**
 * Holds the configuration of the Sling servers that the build should care about.
 *
 * <h2>Configuration</h2>
 *
 * There are four ways to configure server information, in order of specificity (i.e., later ones override prior ones):
 * <ol>
 *     <li>Configuration file</li>
 *     <li>Environment variables</li>
 *     <li>System properties (with caveats; see below)</li>
 *     <li>Project properties</li>
 * </ol>
 *
 * In addition, using the reserved "env" namespace allows for changing the defaults that are applied. See the
 * "Env Namespace" section below.
 *
 * <h2>Configuration File</h2>
 *
 * If the "<code>slingserver.env.file</code>" and "<code>slingserver.env.name</code>" properties are defined, the
 * list of servers for this environment are extracted from a  configuration file. See
 * {@link EnvironmentFileReader} for file format specifics.<p/>
 *
 * Because of the mapping between configuration methods talked about more below, those properties can also be
 * defined as environment variables "<code>SLINGSERVER_ENV_FILE</code>" and "<code>SLINGSERVER_ENV_NAME</code>", or
 * as system properties "<code>envFile</code>" and "<code>environment</code>".
 *
 * <h3>Environment Variables</h3>
 *
 * Environment variables provide a simple and consistent way of defining server configurations that are machine
 * specific, such as developer workstations or continuous integration environments.<p/>
 *
 * To keep the mechanism simple and consistent, environments variables are converted to all lowercase and underscores
 * are converted to periods, then then treated as a project property.<p/>
 *
 * For example, "<code>SLINGSERVER_AUTHOR_PORT</code>" becomes functionally the same as defining the project
 * property "<code>slingserver.author.port</code>". (Though explicitly defining the project property
 * "<code>slingserver.author.port</code>" would override any value set by the environment variable.)<p/>
 *
 * There are a few property names that support simple translation to make them less awkward when used as environment
 * variables. For example, "<code>*_MACHINENAME</code>" (where "*" is the part leading up to the property name) becomes
 * "<code>*.machineName</code>" (camel-case), "<code>*_RETRY_MS</code>" becomes "<code>*.retryWaitMs</code>",
 * and "<code>*_MAX_MS</code>" becomes "<code>*.maxWaitMs</code>"
 *
 * <h2>System Properties</h2>
 *
 * If "<code>envFile</code>" and "<code>environment</code>" system properties are defined, they are translated
 * to "<code>slingserver.env.file</code>" and "<code>slingserver.env.name</code>" respectively.<p/>
 *
 * <strong>NOTE:</strong> This method only supports "envFile" and "environment" for historical reasons and is
 * deprecated. It will likely be removed in a future version.
 *
 * <h2>Project Properties</h2>
 *
 * If project properties have been defined (typically via the -P command line options, but may be in
 * <code>gradle.properties</code> or the like) starting with "<code>slingserver.</code>" then use that to override
 * any System properties or values found in the environment configuration file.<p/>
 *
 * Any other value after the "<code>slingserver.</code>" other than "env" is taken to be the name of the
 * configuration, which then is followed by the property to override. If there isn't a server by that name, it creates
 * a "default" localhost configuration; if the name contains "auth" in it then the default port is set to 4502,
 * otherwise it's assumed to be a publisher and set to 4503.<p/>
 *
 * For example:<ul>
 *     <li>"slingserver.author.port": 4602<ul>
 *         <li>Changes the port of the server named "author" to be 4602</li>
 *     </ul></li>
 *     <li>"slingserver.author2.machineName": test.myco.com<ul>
 *         <li>Creates a new server configuration (assuming there isn't one defined in the environment configuration
 *         file) with a port of 4502 and machineName of test.myco.com</li>
 *     </ul></li>
 *     <li>"slingserver.twinkle2.machineName": test.myco.com<ul>
 *         <li>Creates a new server configuration (assuming there isn't one defined in the environment configuration
 *         file) with a port of 4503 and machineName of test.myco.com</li>
 *     </ul></li>
 * </ul>
 *
 * <h2>Env Namespace</h2>
 *
 * If you specify a property with a "server name" of "env" then that property is applied as the default across all
 * the servers.
 * <p/>
 * For example, if you set the "<code>SlINGSERVER_ENV_USERNAME</code>" environment variable or the
 * "<code>slingserver.env.username</code>" project property then that username value will be applied to every server
 * configuration unless that property has been set for a specific server.
 */
@Slf4j
@CompileStatic
class SlingServersConfiguration implements Iterable<SlingServerConfiguration> {
    /**
     * The name to register this under in the project extensions
     */
    public static final String NAME = 'slingServers'

    public static final String SLINGSERVER_PROP_PREFIX = "slingserver."
    public static final String ENV_FILE_PROJECT_PROPERTY = "${SLINGSERVER_PROP_PREFIX}env.file"
    public static final String ENV_NAME_PROJECT_PROPERTY = "${SLINGSERVER_PROP_PREFIX}env.name"
    public static final String ENV_FILE_SYSTEM_PROPERTY = 'envJson'
    public static final String ENV_NAME_SYSTEM_PROPERTY = 'environment'
    public static final String SLINGSERVER_ENV_PREFIX = "SLINGSERVER_"
    public static final String ENV_FILE_ENV_VAR = "${SLINGSERVER_ENV_PREFIX}ENV_FILE"
    public static final String ENV_NAME_ENV_VAR = "${SLINGSERVER_ENV_PREFIX}ENV_NAME"

    private static final Pattern SERVER_NAME_PATTERN = Pattern.compile(/^slingserver\.([^.]*?)\..*$/)
    private static final Pattern SERVER_PROPERTY_PATTERN = Pattern.compile(/^slingserver\.[^.]*?\.(.*)$/)


    private Map<String, SlingServerConfiguration> _servers

    /**
     * Creates and initializes this using the values in the Project properties, Java System properties,
     * and the System environment.
     */
    // TODO remove hard dependency on Project
    SlingServersConfiguration(@Nonnull Project project) {
        this(project, System.getenv())
    }

    /**
     * Creates and initializes this using the values in the Project properties, Java System properties,
     * and the System environment.
     *
     * @param project the Gradle Project
     * @param env typically {@link System#getenv(String)}, but given here to allow for overriding
     */
    SlingServersConfiguration(@Nonnull Project project, @Nonnull Map<String, String> env) {
        initialize(project, env as EnvironmentVariables)
    }


    private void initialize(Project project, EnvironmentVariables env) {
        if (project == null) throw new IllegalArgumentException("project == null")
        if (env == null) throw new IllegalArgumentException("env == null")

        def configuration = combinedConfiguration(project, env)

        final envFileInfo = envFileInfo(configuration)
        // remove the "special" properties
        configuration.remove(ENV_FILE_PROJECT_PROPERTY)
        configuration.remove(ENV_NAME_PROJECT_PROPERTY)

        ServersMap modifiedServers = computeServerConfs(configuration, envFileInfo)
        log.info "Setting servers configuration to ${modifiedServers}"
        _servers = modifiedServers
    }

    /**
     * If the environment file information is passed in, get the base set of server information from there,
     * otherwise use default localhost information.
     *
     * If the project has properties defining server property overrides, applies those values.
     *
     * @see com.twcable.gradle.sling.EnvironmentFileReader#getServersFromFile(String, String)
     * @see #serversFromConfiguration(Configuration, ServersMap)
     */
    @Nonnull
    protected static ServersMap computeServerConfs(@Nonnull Configuration configuration,
                                                   @Nullable EnvFileInfo envFileInfo) {
        final ServersMap theServers
        if (envFileInfo != null) {
            theServers = EnvironmentFileReader.getServersFromFile(envFileInfo.envFilename, envFileInfo.envName) as ServersMap
        }
        else {
            theServers = new ServersMap()
        }

        log.info "The servers before modification: ${theServers.keySet()}"
        return serversFromConfiguration(configuration, theServers)
    }

    /**
     * If a project property or System property (in that order of precedence) defines an environment file,
     * return that information. Otherwise returns null
     */
    @Nullable
    protected static EnvFileInfo envFileInfo(Configuration configuration) {
        def envFile = configuration.get(ENV_FILE_PROJECT_PROPERTY)
        def envName = configuration.get(ENV_NAME_PROJECT_PROPERTY)

        if (envFile == null && envName == null) return null

        if (envFile != null && envName == null) {
            throw new GradleException("When specifying the environment file, you must also specify the environment" +
                "name using either the ${ENV_NAME_PROJECT_PROPERTY} project property or" +
                "the ${ENV_NAME_ENV_VAR} environment variable")
        }

        if (envFile == null && envName != null) {
            throw new GradleException("When specifying the environment name, you must also specify the environment" +
                "file using either the ${ENV_FILE_PROJECT_PROPERTY} project property or" +
                "the ${ENV_FILE_ENV_VAR} environment variable")
        }

        return new EnvFileInfo(envFilename: envFile, envName: envName)
    }

    /**
     * Merge the configurations found in the Project properties, JVM System properties, and system ENV variables --
     * in that order of precedence.
     */
    @Nullable
    protected static Configuration combinedConfiguration(Project project, EnvironmentVariables systemEnv) {
        // start with environment variables
        def combinedConfiguration = environmentVarsAsConfiguration(systemEnv)

        // allow the system properties to override the environment name
        if (System.getProperty(ENV_NAME_SYSTEM_PROPERTY) != null) {
            log.warn "The \"${ENV_NAME_SYSTEM_PROPERTY}\" system property has been deprecated. Please use " +
                "the \"${ENV_NAME_PROJECT_PROPERTY}\" project property instead. " +
                "(i.e., -P${ENV_NAME_PROJECT_PROPERTY}=\"${System.getProperty(ENV_NAME_SYSTEM_PROPERTY)}\""
            combinedConfiguration.put(ENV_NAME_PROJECT_PROPERTY, System.getProperty(ENV_NAME_SYSTEM_PROPERTY))
        }

        // allow the system properties to override the environment file
        if (System.getProperty(ENV_FILE_SYSTEM_PROPERTY) != null) {
            log.warn "The \"${ENV_FILE_SYSTEM_PROPERTY}\" system property has been deprecated. Please use " +
                "the \"${ENV_FILE_PROJECT_PROPERTY}\" project property instead. " +
                "(i.e., -P${ENV_FILE_PROJECT_PROPERTY}=\"${System.getProperty(ENV_FILE_SYSTEM_PROPERTY)}\""
            combinedConfiguration.put(ENV_FILE_PROJECT_PROPERTY, System.getProperty(ENV_FILE_SYSTEM_PROPERTY))
        }

        // Hack around an edge-condition when testing
        if (!project.projectDir.exists()) project.projectDir.mkdirs()

        // let the project properties override everything
        project.properties.each {
            if (it.key.startsWith(SLINGSERVER_PROP_PREFIX)) {
                combinedConfiguration.put(it.key, it.value)
            }
        }

        return combinedConfiguration
    }

    /**
     * Translate the configuration into the final mapping of server names to {@link SlingServerConfiguration}s.
     * @param configuration the key/values to use to create the end result; if empty then default localhost author/publishers are assumed
     * @param existing any existing mapping loaded from another source
     */
    @Nonnull
    protected static ServersMap serversFromConfiguration(@Nonnull Configuration configuration,
                                                         @Nonnull ServersMap existing) {
        if (existing.isEmpty()) {
            log.info "Did not load any server configurations, so defaulting to a localhost author and publisher"
            configuration.put("${SLINGSERVER_PROP_PREFIX}author.machineName" as String, "localhost")
            configuration.put("${SLINGSERVER_PROP_PREFIX}publisher.machineName" as String, "localhost")
        }

        def serverNames = configuration.
            collect { serverNameFromConfigurationKey(it.key) }.
            findAll { it != 'env' }. // "env" is a reserved server namespace
            unique()

        def configuringServerConfs = serverNames.collectEntries {
            [(it): serverConfForName(it, existing)]
        } as ServersMap
        def serverConfs = (existing + configuringServerConfs) as ServersMap

        changeServerConfsForProjectProps(configuration, serverConfs)

        return serverConfs
    }

    /**
     * Translate system environment values that start with ${SLINGSERVER_ENV_PREFIX} to the same format as
     * Project properties: the key is changed to lowercase and underscores are converted to dots.<p/>
     *
     * For example: SLINGSERVER_AUTHOR_PORT -> slingserver.author.port
     */
    @Nonnull
    protected static Configuration environmentVarsAsConfiguration(EnvironmentVariables systemEnv) {
        EnvironmentVariables serverEnvs = (EnvironmentVariables)systemEnv.findAll {
            ((String)it.key).startsWith(SLINGSERVER_ENV_PREFIX)
        }
        return serverEnvs.collectEntries {
            final configKey = ((String)it.key).toLowerCase().replaceAll("_", ".")
            [(configKey): it.value]
        } as Configuration
    }

    /**
     * For each of the server properties, modify the server configuration for that property.
     *
     * @param configuration the Project properties starting with "slingserver."
     * @param serverConfs the current server configurations
     */
    protected static void changeServerConfsForProjectProps(@Nonnull Configuration configuration,
                                                           @Nonnull ServersMap serverConfs) {

        // apply any "env" properties first
        configuration.
            findAll { serverNameFromConfigurationKey(it.key) == 'env' }.
            each { String k, def v ->
                def propName = serverPropertyFromPropertyKey(k)
                serverConfs.each {
                    log.info "Setting the ${propName} property of ${it.value.name} to ${v}"
                    it.value.setTheProperty(propName, v)
                }
            }

        // apply server-specific (non-"env") properties
        configuration.
            findAll { serverNameFromConfigurationKey(it.key) != 'env' }.
            each { String k, def v ->
                def name = serverNameFromConfigurationKey(k)
                def propName = serverPropertyFromPropertyKey(k)
                def serverConfiguration = serverConfs.get(name) // guaranteed to not be null
                log.info "Setting the ${propName} property of ${name} to ${v}"
                serverConfiguration.setTheProperty(propName, v)
            }
    }


    @Nonnull
    protected static String serverNameFromConfigurationKey(String propKey) throws GradleException {
        return extractPropertyGroup(propKey, SERVER_NAME_PATTERN)
    }


    @Nonnull
    protected static String serverPropertyFromPropertyKey(String propKey) throws GradleException {
        return extractPropertyGroup(propKey, SERVER_PROPERTY_PATTERN)
    }


    @Nonnull
    private static String extractPropertyGroup(String propKey, Pattern pattern) throws GradleException {
        def matcher = pattern.matcher(propKey)
        def matches = matcher.matches()
        if (!matches) throw new GradleException("${propKey} does not match the pattern of \"${SLINGSERVER_PROP_PREFIX}{servername}.{propertyname}\"")
        return matcher.group(1)
    }

    /**
     * If the given server name exists in the existing mapping, return it.
     * Otherwise create a new localhost instance.
     *
     * @see #createServerConfiguration(String)
     */
    @Nonnull
    protected static SlingServerConfiguration serverConfForName(String name, ServersMap existing) {
        if (existing.containsKey(name)) {
            return existing.get(name)
        }
        else {
            return createServerConfiguration(name)
        }
    }

    /**
     * Creates a new localhost instance with uname:pw as admin:admin, using HTTP and the default port, and return it.
     * If the name has "auth" in it then it is assumed to be an author and use port 4502, otherwise 4503.
     */
    @Nonnull
    static SlingServerConfiguration createServerConfiguration(String name) {
        if (name.contains("auth")) {
            log.info "Creating a new author configuration for \"${name}\""

            return new SlingServerConfiguration(
                name: name, protocol: 'http', port: 4502, machineName: 'localhost',
                username: 'admin', password: 'admin')
        }
        else {
            log.info "Creating a new publisher configuration for \"${name}\""

            return new SlingServerConfiguration(
                name: name, protocol: 'http', port: 4503, machineName: 'localhost',
                username: 'admin', password: 'admin')
        }
    }


    Map<String, SlingServerConfiguration> getServers() {
        return _servers
    }


    void propertyMissing(String name, SlingServerConfiguration configuration) {
        servers.put(name, configuration)
    }


    SlingServerConfiguration propertyMissing(String name) {
        servers.get(name)
    }


    SlingServerConfiguration getAt(String name) {
        servers.get(name)
    }


    void putAt(String name, SlingServerConfiguration configuration) {
        servers.put(name, configuration)
    }


    @Override
    Iterator<SlingServerConfiguration> iterator() {
        servers.values().findAll { it.active }.iterator()
    }


    @Override
    public String toString() {
        return "SlingServersConfiguration{" + _servers + '}';
    }

    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************

    /**
     * Tuple for holding information about loading envionment configurations from a file
     */
    @Immutable
    protected static class EnvFileInfo {
        String envFilename
        String envName
    }

    /**
     * "Named type" for working with the results of System.getenv()
     */
    @InheritConstructors
    protected static class EnvironmentVariables extends LinkedHashMap<String, String> {}

    /**
     * "Named type" for working with the a mapping of server environment configuration keys and their values
     */
    @InheritConstructors
    protected static class Configuration extends LinkedHashMap<String, Object> {}

    /**
     * "Named type" for working with a mapping of server names to their configurations
     */
    @InheritConstructors
    protected static class ServersMap extends LinkedHashMap<String, SlingServerConfiguration> {}

}
