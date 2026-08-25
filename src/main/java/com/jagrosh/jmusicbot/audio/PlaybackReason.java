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
package com.jagrosh.jmusicbot.audio;

/**
 * Why the now-playing panel currently shows what it shows.
 *
 * <p>Replaces a plain {@code String} that served two incompatible purposes at once: it was
 * both the footer text shown to users and the value the track-change logic branched on, via
 *
 * <pre>
 *   if (lastReason == null
 *       || (!lastReason.startsWith("Repeating") &amp;&amp; !lastReason.startsWith("Skipped")))
 *       lastReason = "Playing next song.";
 * </pre>
 *
 * <p>That coupling hid a live bug. Callers set the reason to
 * {@code member.getUser().getName() + " skipped forward."}, so the string began with a
 * username and used a lowercase verb — {@code startsWith("Skipped")} could never match. The
 * skip footer was overwritten by "Playing next song." on every track change, and the branch
 * was effectively dead. Separating the two roles fixes it: {@link Kind#survivesTrackChange()}
 * is now an explicit property rather than a guess made from prose.
 *
 * <p>It also makes the footer translatable. While display text doubled as control flow,
 * translating it would have silently changed which branch ran.
 *
 * @param kind  what happened
 * @param actor display name of the user responsible, or {@code null} for events with no user
 *
 * @author adan (xx445469)
 */
public record PlaybackReason(Kind kind, String actor)
{
    /**
     * A reason, and the message key that renders it.
     *
     * <p>Keys taking an actor use {@code {0}} for the display name.
     */
    public enum Kind
    {
        /** Repeat mode re-queued the finished track at the end. */
        REPEATING_QUEUE("nowplaying.footer.repeatingQueue", false, true),
        /** Repeat mode re-queued the finished track immediately. */
        REPEATING_SONG("nowplaying.footer.repeatingSong", false, true),
        /** Ordinary advance to the next queued track. */
        PLAYING_NEXT("nowplaying.footer.playingNextSong", false, false),

        /** A user skipped the current track. */
        SKIPPED_FORWARD("nowplaying.footer.skippedForward", true, true),
        /** A user added a track to the end of the queue. */
        ADDED_TO_QUEUE("nowplaying.footer.addedQueue", true, false),
        /** A user added a track to the front of the queue. */
        ADDED_TO_FRONT("nowplaying.footer.addedFront", true, false),
        /** A user re-queued one track from playback history. */
        ADDED_FROM_HISTORY("nowplaying.footer.addedFromHistory", true, false),
        /** A user re-queued the entire playback history. */
        ADDED_ALL_HISTORY("nowplaying.footer.addedAllHistory", true, false),
        /** A user started playing a track straight from history. */
        PLAYING_FROM_HISTORY("nowplaying.footer.playingFromHistory", true, false),
        /** A user added a playlist. */
        ADDED_PLAYLIST("nowplaying.footer.addedPlaylist", true, false);

        private final String messageKey;
        private final boolean takesActor;
        private final boolean survivesTrackChange;

        Kind(String messageKey, boolean takesActor, boolean survivesTrackChange)
        {
            this.messageKey = messageKey;
            this.takesActor = takesActor;
            this.survivesTrackChange = survivesTrackChange;
        }

        /** Translation key for this reason's footer text. */
        public String getMessageKey()
        {
            return messageKey;
        }

        /** Whether this reason's message expects an actor name as {@code {0}}. */
        public boolean takesActor()
        {
            return takesActor;
        }

        /**
         * Whether this reason should still be shown after the player advances to the next
         * track.
         *
         * <p>True for reasons that explain the advance itself — repeating and skipping.
         * False for reasons describing an earlier action, which would be stale and
         * misleading once a different track is playing.
         */
        public boolean survivesTrackChange()
        {
            return survivesTrackChange;
        }
    }

    /** A reason with no user attached. */
    public static PlaybackReason of(Kind kind)
    {
        return new PlaybackReason(kind, null);
    }

    /** A reason attributed to a user. */
    public static PlaybackReason by(Kind kind, String actor)
    {
        return new PlaybackReason(kind, actor);
    }

    /** Arguments for the message template: the actor, or none. */
    public Object[] arguments()
    {
        return kind.takesActor() ? new Object[] { actor == null ? "" : actor } : new Object[0];
    }
}
