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
package com.jagrosh.jmusicbot.audio.lavalink;

/**
 * A track as Lavalink describes it: the opaque {@code encoded} string the node needs back to
 * play it, plus the bits of {@code info} stage 1 actually displays.
 *
 * <p>Deliberately not {@code com.sedmelluq...AudioTrack} — that type means "a track Lavaplayer
 * can decode locally", which is not what this is. Keeping this as its own small type is the
 * boundary described in {@link LavalinkPlaybackEngine}: nothing outside the {@code lavalink}
 * package needs to know the shape of a Lavalink track.
 *
 * @param encoded    opaque track string the node uses to identify/replay this track
 * @param identifier the source-specific identifier (e.g. a YouTube video id)
 * @param title      display title
 * @param author     display author/uploader
 * @param uri        canonical URL, or {@code null} if the source does not have one
 * @param length     duration in milliseconds
 * @param stream     whether this is a live/indefinite stream (no fixed length)
 * @param requesterId Discord user id who queued this track, or 0 if none
 *
 * @author adan (xx445469)
 */
public record LavalinkTrack(String encoded, String identifier, String title, String author,
                             String uri, long length, boolean stream, long requesterId)
{
    public LavalinkTrack withRequester(long newRequesterId)
    {
        return new LavalinkTrack(encoded, identifier, title, author, uri, length, stream, newRequesterId);
    }
}
