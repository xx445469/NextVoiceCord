/*
 * Copyright 2026 adan (xx445469) - NextVoiceCord
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

import com.jagrosh.jmusicbot.utils.YoutubeDeviceCodeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("YoutubeDeviceCodeUrl Tests")
class YoutubeDeviceCodeUrlTest {

    @Test
    @DisplayName("adds user_code to a bare URL with a leading '?'")
    void addsQueryParamToBareUrl() {
        String result = YoutubeDeviceCodeUrl.withCode("https://www.google.com/device", "ABCD-EFGH");
        assertEquals("https://www.google.com/device?user_code=ABCD-EFGH", result);
    }

    @Test
    @DisplayName("appends with '&' when the captured URL already carries a query string")
    void appendsWithAmpersandWhenQueryStringAlreadyPresent() {
        String result = YoutubeDeviceCodeUrl.withCode("https://www.google.com/device?foo=bar", "ABCD-EFGH");
        assertEquals("https://www.google.com/device?foo=bar&user_code=ABCD-EFGH", result);
    }

    @Test
    @DisplayName("does not hard-code the host: whatever youtube-source supplies is preserved")
    void preservesWhateverHostWasSupplied() {
        String result = YoutubeDeviceCodeUrl.withCode("https://example.org/somewhere/else", "XYZ");
        assertEquals("https://example.org/somewhere/else?user_code=XYZ", result);
    }

    @Test
    @DisplayName("URL-encodes a code that needs it")
    void urlEncodesTheCode() {
        String result = YoutubeDeviceCodeUrl.withCode("https://www.google.com/device", "AB CD+EF");
        assertEquals("https://www.google.com/device?user_code=AB+CD%2BEF", result);
    }

    @Test
    @DisplayName("returns the URL unchanged when the code is missing")
    void returnsUrlUnchangedWhenCodeMissing() {
        assertEquals("https://www.google.com/device", YoutubeDeviceCodeUrl.withCode("https://www.google.com/device", null));
        assertEquals("https://www.google.com/device", YoutubeDeviceCodeUrl.withCode("https://www.google.com/device", ""));
    }

    @Test
    @DisplayName("returns null when the URL itself is missing")
    void returnsNullWhenUrlMissing() {
        assertNull(YoutubeDeviceCodeUrl.withCode(null, "ABCD-EFGH"));
    }
}
