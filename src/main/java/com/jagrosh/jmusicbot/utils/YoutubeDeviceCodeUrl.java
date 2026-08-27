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
package com.jagrosh.jmusicbot.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds a YouTube device-flow verification link with the code already filled in.
 *
 * <p>Google's device flow accepts the code as a {@code user_code} query parameter on the
 * verification page — verified against the live endpoint: both
 * {@code https://www.google.com/device} and {@code https://www.google.com/device?user_code=…}
 * return 200. Building on that means whoever presses the sign-in button lands on a page that
 * already has the code typed in, rather than having to copy eight characters by hand.
 *
 * <p>The host is never hard-coded here: {@code youtube-source} supplies the verification URL in
 * the log line this whole feature is built on ({@link YoutubeOauth2TokenHandler}), and that
 * library — not this one — owns which host device-flow verification happens against.
 *
 * @author adan (xx445469)
 */
public final class YoutubeDeviceCodeUrl
{
    private static final String PARAM = "user_code";

    private YoutubeDeviceCodeUrl() { }

    /**
     * Appends {@code user_code=<code>} to {@code authorisationUrl}, using {@code &} instead of
     * {@code ?} if the captured URL already carries a query string.
     *
     * @return the URL with the code pre-filled, or {@code authorisationUrl} unchanged if either
     *         argument is missing — a best-effort helper has nothing sensible to build from a
     *         URL or code that never arrived.
     */
    public static String withCode(String authorisationUrl, String code)
    {
        if (authorisationUrl == null || authorisationUrl.isBlank() || code == null || code.isBlank())
        {
            return authorisationUrl;
        }

        char separator = authorisationUrl.indexOf('?') >= 0 ? '&' : '?';
        String encoded = URLEncoder.encode(code, StandardCharsets.UTF_8);
        return authorisationUrl + separator + PARAM + "=" + encoded;
    }
}
