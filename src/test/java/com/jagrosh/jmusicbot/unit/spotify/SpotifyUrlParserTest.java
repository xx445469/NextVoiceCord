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
package com.jagrosh.jmusicbot.unit.spotify;

import com.jagrosh.jmusicbot.spotify.SpotifyReference;
import com.jagrosh.jmusicbot.spotify.SpotifyUrlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SpotifyUrlParser")
class SpotifyUrlParserTest
{
    private static Stream<Arguments> validLinks()
    {
        return Stream.of(
                // URL form
                Arguments.of("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
                        SpotifyReference.EntityType.TRACK, "4cOdK2wGLETKBW3PvgPWqT"),
                Arguments.of("https://open.spotify.com/album/1DFixLWuPkv3KT3TnV35m3",
                        SpotifyReference.EntityType.ALBUM, "1DFixLWuPkv3KT3TnV35m3"),
                Arguments.of("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
                        SpotifyReference.EntityType.PLAYLIST, "37i9dQZF1DXcBWIGoYBM5M"),
                Arguments.of("https://open.spotify.com/artist/06HL4z0CvFAxyc27GXpf02",
                        SpotifyReference.EntityType.ARTIST, "06HL4z0CvFAxyc27GXpf02"),

                // URL with query string / fragment, as Discord users routinely paste
                Arguments.of("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=abc123",
                        SpotifyReference.EntityType.TRACK, "4cOdK2wGLETKBW3PvgPWqT"),
                Arguments.of("http://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
                        SpotifyReference.EntityType.TRACK, "4cOdK2wGLETKBW3PvgPWqT"),

                // Localised path segment
                Arguments.of("https://open.spotify.com/intl-de/track/4cOdK2wGLETKBW3PvgPWqT",
                        SpotifyReference.EntityType.TRACK, "4cOdK2wGLETKBW3PvgPWqT"),

                // URI form
                Arguments.of("spotify:track:4cOdK2wGLETKBW3PvgPWqT",
                        SpotifyReference.EntityType.TRACK, "4cOdK2wGLETKBW3PvgPWqT"),
                Arguments.of("spotify:album:1DFixLWuPkv3KT3TnV35m3",
                        SpotifyReference.EntityType.ALBUM, "1DFixLWuPkv3KT3TnV35m3"),
                Arguments.of("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
                        SpotifyReference.EntityType.PLAYLIST, "37i9dQZF1DXcBWIGoYBM5M"),
                Arguments.of("spotify:artist:06HL4z0CvFAxyc27GXpf02",
                        SpotifyReference.EntityType.ARTIST, "06HL4z0CvFAxyc27GXpf02")
        );
    }

    @ParameterizedTest(name = "{index}: {0} -> {1}/{2}")
    @MethodSource("validLinks")
    @DisplayName("parses all four entity types in both URL and URI form")
    void parsesValidLinks(String input, SpotifyReference.EntityType expectedType, String expectedId)
    {
        Optional<SpotifyReference> result = SpotifyUrlParser.parse(input);
        assertTrue(result.isPresent(), "Expected " + input + " to parse");
        assertEquals(expectedType, result.get().type());
        assertEquals(expectedId, result.get().id());
        assertTrue(SpotifyUrlParser.isSpotifyReference(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://soundcloud.com/artist/track",
            "not a url at all",
            "spotify:show:4rOoJ6Egrf8K2IrywzwOMk",   // podcast show: not a supported entity type
            "https://open.spotify.com/user/someuser",  // not a supported entity type
            "https://open.spotify.com/track/",         // missing id
            "spotify:track:",                          // missing id
            ""
    })
    @DisplayName("rejects non-Spotify or unsupported links")
    void rejectsNonSpotifyLinks(String input)
    {
        assertEquals(Optional.empty(), SpotifyUrlParser.parse(input));
        assertFalse(SpotifyUrlParser.isSpotifyReference(input));
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull()
    {
        assertEquals(Optional.empty(), SpotifyUrlParser.parse(null));
        assertFalse(SpotifyUrlParser.isSpotifyReference(null));
    }

    @Test
    @DisplayName("is case-insensitive on scheme and entity type")
    void isCaseInsensitive()
    {
        Optional<SpotifyReference> result = SpotifyUrlParser.parse("SPOTIFY:TRACK:4cOdK2wGLETKBW3PvgPWqT");
        assertTrue(result.isPresent());
        assertEquals(SpotifyReference.EntityType.TRACK, result.get().type());
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", result.get().id());
    }

    @Test
    @DisplayName("trims surrounding whitespace")
    void trimsWhitespace()
    {
        Optional<SpotifyReference> result = SpotifyUrlParser.parse("  spotify:track:4cOdK2wGLETKBW3PvgPWqT  ");
        assertTrue(result.isPresent());
    }
}
