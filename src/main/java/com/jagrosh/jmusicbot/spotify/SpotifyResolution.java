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

import java.util.List;

/**
 * What a Spotify Web API lookup produced: a display name and a list of YouTube search queries
 * (one per Spotify track, formatted as {@code "artist - title"}), plus the bookkeeping needed to
 * be honest with the user about what was actually loaded.
 *
 * @param type            the kind of link this came from
 * @param entityName       the track/album/playlist/artist name, as Spotify has it
 * @param searchQueries    one YouTube search query per Spotify track actually considered (after capping)
 * @param totalAvailable   how many tracks Spotify reports this entity has in total
 * @param capped           whether {@code totalAvailable} exceeds what was fetched
 * @param capLimit          the cap that was applied, or 0 if nothing was capped
 *
 * @author adan (xx445469)
 */
public record SpotifyResolution(
        SpotifyReference.EntityType type,
        String entityName,
        List<String> searchQueries,
        int totalAvailable,
        boolean capped,
        int capLimit)
{
}
