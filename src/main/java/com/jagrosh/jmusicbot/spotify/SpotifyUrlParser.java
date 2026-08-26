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
package com.jagrosh.jmusicbot.spotify;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises Spotify track/album/playlist/artist links, in both forms Spotify itself hands out:
 * a web URL ({@code https://open.spotify.com/track/...}) and a URI ({@code spotify:track:...}).
 *
 * <p>Neither form carries anything playable — Spotify's audio is DRM-protected and there is no
 * public streaming API for it. Parsing one only tells the caller which Spotify Web API lookup to
 * make; what plays afterwards is always a YouTube match.
 *
 * @author adan (xx445469)
 */
public final class SpotifyUrlParser
{
    // open.spotify.com sometimes prefixes the path with a locale, e.g. "intl-de/track/...".
    private static final Pattern OPEN_URL = Pattern.compile(
            "(?i)^https?://open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?"
                    + "(track|album|playlist|artist)/([A-Za-z0-9]+)(?:[/?#].*)?$");

    private static final Pattern URI = Pattern.compile(
            "(?i)^spotify:(track|album|playlist|artist):([A-Za-z0-9]+)$");

    private SpotifyUrlParser()
    {
    }

    /**
     * Parses a Spotify link.
     *
     * @param input the raw command argument or track identifier; may be {@code null}
     * @return the parsed reference, or empty if this is not a Spotify track/album/playlist/artist link
     */
    public static Optional<SpotifyReference> parse(String input)
    {
        if (input == null)
        {
            return Optional.empty();
        }

        String trimmed = input.trim();

        Matcher urlMatch = OPEN_URL.matcher(trimmed);
        if (urlMatch.matches())
        {
            return Optional.of(toReference(urlMatch));
        }

        Matcher uriMatch = URI.matcher(trimmed);
        if (uriMatch.matches())
        {
            return Optional.of(toReference(uriMatch));
        }

        return Optional.empty();
    }

    /** Whether {@code input} is a Spotify link of any of the four supported kinds. */
    public static boolean isSpotifyReference(String input)
    {
        return parse(input).isPresent();
    }

    private static SpotifyReference toReference(Matcher matcher)
    {
        SpotifyReference.EntityType type =
                SpotifyReference.EntityType.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
        return new SpotifyReference(type, matcher.group(2));
    }
}
