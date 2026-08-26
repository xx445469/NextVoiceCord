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

/**
 * A parsed Spotify link: what kind of thing it points at, and its Spotify id.
 *
 * @author adan (xx445469)
 */
public record SpotifyReference(EntityType type, String id)
{
    /** The four link shapes the Spotify Web API can resolve to a track list. */
    public enum EntityType
    {
        TRACK,
        ALBUM,
        PLAYLIST,
        ARTIST
    }
}
