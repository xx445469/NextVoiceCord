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
package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.ProxyUtil;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetSocketAddress;
import java.net.Proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("ProxyUtil Tests")
class ProxyUtilTest {

    @Mock
    private BotConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("createProxy() Tests")
    class CreateProxyTests {

        @Test
        @DisplayName("createProxy() returns null when proxy is not configured")
        void createProxyReturnsNullWhenNotConfigured() {
            when(config.hasProxy()).thenReturn(false);

            Proxy result = ProxyUtil.createProxy(config);

            assertNull(result);
        }

        @Test
        @DisplayName("createProxy() returns HTTP proxy when configured")
        void createProxyReturnsHttpProxyWhenConfigured() {
            when(config.hasProxy()).thenReturn(true);
            when(config.getProxyHost()).thenReturn("127.0.0.1");
            when(config.getProxyPort()).thenReturn(8080);

            Proxy result = ProxyUtil.createProxy(config);

            assertNotNull(result);
            assertEquals(Proxy.Type.HTTP, result.type());
            
            InetSocketAddress address = (InetSocketAddress) result.address();
            assertEquals("127.0.0.1", address.getHostString());
            assertEquals(8080, address.getPort());
        }

        @Test
        @DisplayName("createProxy() handles different host and port values")
        void createProxyHandlesDifferentValues() {
            when(config.hasProxy()).thenReturn(true);
            when(config.getProxyHost()).thenReturn("proxy.example.com");
            when(config.getProxyPort()).thenReturn(18080);

            Proxy result = ProxyUtil.createProxy(config);

            assertNotNull(result);
            InetSocketAddress address = (InetSocketAddress) result.address();
            assertEquals("proxy.example.com", address.getHostString());
            assertEquals(18080, address.getPort());
        }
    }

    @Nested
    @DisplayName("createApacheProxy() Tests")
    class CreateApacheProxyTests {

        @Test
        @DisplayName("createApacheProxy() returns null when proxy is not configured")
        void createApacheProxyReturnsNullWhenNotConfigured() {
            when(config.hasProxy()).thenReturn(false);

            HttpHost result = ProxyUtil.createApacheProxy(config);

            assertNull(result);
        }

        @Test
        @DisplayName("createApacheProxy() returns HttpHost when configured")
        void createApacheProxyReturnsHttpHostWhenConfigured() {
            when(config.hasProxy()).thenReturn(true);
            when(config.getProxyHost()).thenReturn("127.0.0.1");
            when(config.getProxyPort()).thenReturn(8080);

            HttpHost result = ProxyUtil.createApacheProxy(config);

            assertNotNull(result);
            assertEquals("127.0.0.1", result.getHostName());
            assertEquals(8080, result.getPort());
        }

        @Test
        @DisplayName("createApacheProxy() handles different host and port values")
        void createApacheProxyHandlesDifferentValues() {
            when(config.hasProxy()).thenReturn(true);
            when(config.getProxyHost()).thenReturn("proxy.example.com");
            when(config.getProxyPort()).thenReturn(3128);

            HttpHost result = ProxyUtil.createApacheProxy(config);

            assertNotNull(result);
            assertEquals("proxy.example.com", result.getHostName());
            assertEquals(3128, result.getPort());
        }
    }
}
