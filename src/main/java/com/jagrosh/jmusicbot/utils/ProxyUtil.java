/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Modifications copyright 2026 adan (xx445469) - NextVoiceCord.
 * Changes: added username/password authentication for proxies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.utils;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;

import com.jagrosh.jmusicbot.BotConfig;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;

/**
 * Builds proxy configuration for the three HTTP stacks the bot runs on.
 *
 * <p>Lavaplayer uses Apache HttpClient, JDA uses OkHttp, and version checks use
 * {@code HttpURLConnection}. None of them share a way to carry proxy credentials, so
 * authentication has to be expressed three times over.
 *
 * @author Arif Banai (arif-banai), adan (xx445469)
 */
public class ProxyUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyUtil.class);

    /** Set once, because installing a JVM-wide Authenticator repeatedly serves no purpose. */
    private static volatile boolean jvmAuthenticatorInstalled = false;

    /**
     * Creates a Java Proxy object from config settings.
     * Used for OkHttp-based clients (JDA, GitHub checks).
     *
     * @param config The bot configuration
     * @return A Proxy object, or null if proxy is not configured
     */
    public static Proxy createProxy(BotConfig config) {
        if (!config.hasProxy()) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP,
                new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
    }

    /**
     * Creates an Apache HttpHost for Lavaplayer's HTTP client.
     * Used with DefaultAudioPlayerManager.setHttpBuilderConfigurator().
     *
     * @param config The bot configuration
     * @return An HttpHost object, or null if proxy is not configured
     */
    public static HttpHost createApacheProxy(BotConfig config) {
        if (!config.hasProxy()) {
            return null;
        }
        return new HttpHost(config.getProxyHost(), config.getProxyPort());
    }

    /** Whether the configured proxy expects a username and password. */
    public static boolean hasCredentials(BotConfig config) {
        return config.hasProxy()
                && config.getProxyUsername() != null && !config.getProxyUsername().isBlank();
    }

    /**
     * Credentials for Lavaplayer's Apache HttpClient.
     *
     * <p>Scoped to the proxy host and port rather than {@code AuthScope.ANY}, so the
     * credentials cannot be offered to YouTube or any other origin the bot talks to.
     *
     * @return a provider, or null when the proxy needs no authentication
     */
    public static CredentialsProvider createApacheCredentials(BotConfig config) {
        if (!hasCredentials(config)) {
            return null;
        }
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(
                new AuthScope(config.getProxyHost(), config.getProxyPort()),
                new UsernamePasswordCredentials(config.getProxyUsername(), config.getProxyPassword()));
        return provider;
    }

    /**
     * Adds proxy authentication to an OkHttp builder, for JDA.
     *
     * <p>OkHttp asks for credentials only after the proxy answers 407, and re-asks on every
     * new connection. Returning null when a previous attempt already carried the header stops
     * an endless retry loop against a proxy that rejects these credentials.
     */
    public static OkHttpClient.Builder applyOkHttpCredentials(OkHttpClient.Builder builder, BotConfig config) {
        if (!hasCredentials(config)) {
            return builder;
        }

        String header = Credentials.basic(config.getProxyUsername(), config.getProxyPassword());
        return builder.proxyAuthenticator((route, response) -> {
            if (response.request().header("Proxy-Authorization") != null) {
                return null;
            }
            return response.request().newBuilder()
                           .header("Proxy-Authorization", header)
                           .build();
        });
    }

    /**
     * Installs a JVM-wide authenticator for {@code HttpURLConnection}, used by version checks.
     *
     * <p>This is process-global, which is why it checks the requestor type: without that, the
     * proxy password would be offered to any server that returns a 401, not just the proxy.
     */
    public static synchronized void installJvmAuthenticator(BotConfig config) {
        if (jvmAuthenticatorInstalled || !hasCredentials(config)) {
            return;
        }

        String host = config.getProxyHost();
        int port = config.getProxyPort();
        String user = config.getProxyUsername();
        char[] password = config.getProxyPassword().toCharArray();

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY
                        && host.equalsIgnoreCase(getRequestingHost())
                        && port == getRequestingPort()) {
                    return new PasswordAuthentication(user, password);
                }
                return null;
            }
        });

        // Basic proxy auth over plain HTTP is disabled by default in modern JDKs. Without
        // clearing this, authentication fails with no explanation of why.
        if (System.getProperty("jdk.http.auth.tunneling.disabledSchemes") == null) {
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        }
        if (System.getProperty("jdk.http.auth.proxying.disabledSchemes") == null) {
            System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
        }

        jvmAuthenticatorInstalled = true;
        LOG.debug("Proxy authenticator installed for {}:{}", host, port);
    }

    /** Host and port, never the credentials — this ends up in logs. */
    public static String describe(BotConfig config) {
        return config.getProxyHost() + ":" + config.getProxyPort()
                + (hasCredentials(config) ? " (authenticated)" : "");
    }
}
