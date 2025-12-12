/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.client.impl.auth.oauth2;

import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.Timer;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.PulsarVersion;
import org.apache.pulsar.client.api.AuthenticationInitContext;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.DnsResolverGroupImpl;
import org.apache.pulsar.client.impl.auth.oauth2.protocol.DefaultMetadataResolver;
import org.apache.pulsar.client.impl.auth.oauth2.protocol.Metadata;
import org.apache.pulsar.client.impl.auth.oauth2.protocol.MetadataResolver;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;

/**
 * An abstract OAuth 2.0 authorization flow.
 */
@Slf4j
abstract class FlowBase implements Flow {

    public static final String CONFIG_PARAM_CONNECT_TIMEOUT = "connectTimeout";
    public static final String CONFIG_PARAM_READ_TIMEOUT = "readTimeout";
    public static final String CONFIG_PARAM_TRUST_CERTS_FILE_PATH = "trustCertsFilePath";

    protected static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    protected static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    private static final long serialVersionUID = 1L;

    protected final URL issuerUrl;
    protected final Duration connectTimeout;
    protected final Duration readTimeout;
    protected final String trustCertsFilePath;
    protected AsyncHttpClient httpClient;

    protected transient Metadata metadata;

    private transient EventLoopGroup eventLoopGroup;
    private transient DnsResolverGroupImpl dnsResolverGroup;
    private transient Timer timer;

    protected FlowBase(URL issuerUrl, Duration connectTimeout, Duration readTimeout, String trustCertsFilePath) {
        this.issuerUrl = issuerUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.trustCertsFilePath = trustCertsFilePath;
    }

    private AsyncHttpClient createHttpClient() {
        DefaultAsyncHttpClientConfig.Builder confBuilder = new DefaultAsyncHttpClientConfig.Builder();
        confBuilder.setCookieStore(null);
        confBuilder.setUseProxyProperties(true);
        confBuilder.setFollowRedirect(true);
        confBuilder.setConnectTimeout(
                getParameterDurationToMillis(CONFIG_PARAM_CONNECT_TIMEOUT, this.connectTimeout,
                        DEFAULT_CONNECT_TIMEOUT));
        confBuilder.setReadTimeout(
                getParameterDurationToMillis(CONFIG_PARAM_READ_TIMEOUT, this.readTimeout, DEFAULT_READ_TIMEOUT));
        confBuilder.setUserAgent(String.format("Pulsar-Java-v%s", PulsarVersion.getVersion()));

        // Use shared EventLoopGroup if available
        if (eventLoopGroup != null) {
            confBuilder.setEventLoopGroup(eventLoopGroup);
            log.debug("Using shared EventLoopGroup for OAuth2 HTTP client");
        }

        // Use shared timer if available
        if (timer != null) {
            confBuilder.setNettyTimer(timer);
            log.debug("Shared Timer available for OAuth2 HTTP client");
        }

        if (StringUtils.isNotBlank(trustCertsFilePath)) {
            try {
                confBuilder.setSslContext(SslContextBuilder.forClient()
                        .trustManager(new File(trustCertsFilePath))
                        .build());
            } catch (SSLException e) {
                log.error("Could not set " + CONFIG_PARAM_TRUST_CERTS_FILE_PATH, e);
            }
        }
        return new DefaultAsyncHttpClient(confBuilder.build());
    }

    private int getParameterDurationToMillis(String name, Duration value, Duration defaultValue) {
        Duration duration;
        if (value == null) {
            log.info("Configuration for [{}] is using the default value: [{}]", name, defaultValue);
            duration = defaultValue;
        } else {
            log.info("Configuration for [{}] is: [{}]", name, value);
            duration = value;
        }

        return (int) duration.toMillis();
    }

    public void initialize() throws PulsarClientException {
        // Create default HTTP client
        if (this.httpClient == null) {
            this.httpClient = createHttpClient();
        }
        try {
            this.metadata = createMetadataResolver().resolve();
        } catch (IOException e) {
            log.error("Unable to retrieve OAuth 2.0 server metadata", e);
            throw new PulsarClientException.AuthenticationException("Unable to retrieve OAuth 2.0 server metadata");
        }
    }

    public void initialize(AuthenticationInitContext context) throws PulsarClientException {
        Optional<EventLoopGroup> sharedEventLoop = context.getService(EventLoopGroup.class);
        Optional<Timer> sharedTimer = context.getService(Timer.class);
        Optional<DnsResolverGroupImpl> sharedDnsResolver = context.getService(DnsResolverGroupImpl.class);

        boolean shouldRecreateHttpClient = false;

        // Store EventLoopGroup if available (most important)
        if (sharedEventLoop.isPresent()) {
            this.eventLoopGroup = sharedEventLoop.get();
            shouldRecreateHttpClient = true;
            log.debug("Using shared EventLoopGroup");
        }

        // Store DnsResolverGroup if available
        if (sharedDnsResolver.isPresent()) {
            this.dnsResolverGroup = sharedDnsResolver.get();
            log.debug("Shared DnsResolverGroup available");
            // TODO: Integrate with AsyncHttpClient in Phase 2
        }

        // Store Timer if available
        if (sharedTimer.isPresent()) {
            this.timer = sharedTimer.get();
            log.debug("Shared Timer available");
            // TODO: Use for token refresh in Phase 3
        }

        // Recreate HTTP client if EventLoopGroup changed
        if (shouldRecreateHttpClient && this.httpClient != null) {
            try {
                this.httpClient.close();
            } catch (IOException e) {
                log.warn("Error closing existing HTTP client", e);
            }
            this.httpClient = null; // Will be recreated in initialize()
        }

        initialize();
    }

    protected MetadataResolver createMetadataResolver() {
        return DefaultMetadataResolver.fromIssuerUrl(issuerUrl, httpClient);
    }

    static String parseParameterString(Map<String, String> params, String name) {
        String s = params.get(name);
        if (StringUtils.isEmpty(s)) {
            throw new IllegalArgumentException("Required configuration parameter: " + name);
        }
        return s;
    }

    static URL parseParameterUrl(Map<String, String> params, String name) {
        String s = params.get(name);
        if (StringUtils.isEmpty(s)) {
            throw new IllegalArgumentException("Required configuration parameter: " + name);
        }
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed configuration parameter: " + name);
        }
    }

    static Duration parseParameterDuration(Map<String, String> params, String name) {
        String value = params.get(name);
        if (StringUtils.isNotBlank(value)) {
            try {
                return Duration.parse(value);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Malformed configuration parameter: " + name, e);
            }
        }
        return null;
    }

    @Override
    public void close() throws Exception {
        httpClient.close();
    }
}
