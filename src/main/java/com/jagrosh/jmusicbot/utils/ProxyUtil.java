/*
 * Copyright 2026 Arif Banai (arif-banai)
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

import com.jagrosh.jmusicbot.BotConfig;
import org.apache.http.HttpHost;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Utility class for creating proxy objects from configuration.
 * Supports both Java's native Proxy (for OkHttp/JDA) and Apache's HttpHost (for Lavaplayer).
 *
 * @author Arif Banai (arif-banai)
 */
public class ProxyUtil {

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
}
