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
package com.jagrosh.jmusicbot.unit.update;

import com.jagrosh.jmusicbot.update.UpdateChecker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Version comparison, which decides whether the bot replaces its own binary.
 *
 * <p>Both failure directions are silent and slow to notice: a bot that stops seeing updates
 * simply looks like a project that stopped releasing, and one that installs an older build
 * looks like a regression in the code.
 */
@DisplayName("UpdateChecker.isNewer")
class UpdateCheckerVersionTest
{
    @Nested
    @DisplayName("numeric ordering")
    class NumericOrdering
    {
        @Test
        @DisplayName("compares segments as numbers, not text")
        void comparesNumerically()
        {
            // The reason string comparison cannot be used: "0.10.0" sorts before "0.9.0"
            // lexically, so updates would have quietly stopped at the first double digit.
            assertTrue(UpdateChecker.isNewer("0.9.0", "0.10.0"));
            assertFalse(UpdateChecker.isNewer("0.10.0", "0.9.0"));
        }

        @Test
        @DisplayName("treats missing trailing segments as zero")
        void missingSegmentsAreZero()
        {
            assertFalse(UpdateChecker.isNewer("1.2.0", "1.2"));
            assertTrue(UpdateChecker.isNewer("1.2", "1.2.1"));
        }

        @Test
        @DisplayName("an identical version is not newer")
        void identicalIsNotNewer()
        {
            assertFalse(UpdateChecker.isNewer("1.2.3", "1.2.3"));
        }
    }

    @Nested
    @DisplayName("pre-releases")
    class PreReleases
    {
        @Test
        @DisplayName("a stable release supersedes its own pre-release")
        void stableBeatsPreRelease()
        {
            // Getting this wrong strands every beta install: the release it was waiting for
            // arrives and is never seen, because the suffix made the beta look newer.
            assertTrue(UpdateChecker.isNewer("0.8.0-beta.1", "0.8.0"));
        }

        @Test
        @DisplayName("a pre-release never supersedes a stable release")
        void preReleaseNeverBeatsStable()
        {
            // The mirror image, and the worse one: a stable install would downgrade itself
            // onto a beta on its own.
            assertFalse(UpdateChecker.isNewer("0.8.0", "0.8.0-beta.1"));
        }

        @Test
        @DisplayName("pre-releases order among themselves")
        void preReleasesOrderAmongThemselves()
        {
            assertTrue(UpdateChecker.isNewer("0.8.0-beta.1", "0.8.0-beta.2"));
            assertFalse(UpdateChecker.isNewer("0.8.0-beta.2", "0.8.0-beta.1"));
            assertTrue(UpdateChecker.isNewer("0.8.0-alpha.1", "0.8.0-beta.1"));
        }

        @Test
        @DisplayName("a higher core version wins regardless of suffix")
        void coreVersionDominates()
        {
            assertTrue(UpdateChecker.isNewer("0.8.0-beta.1", "0.9.0"));
            assertTrue(UpdateChecker.isNewer("0.8.0", "0.9.0-beta.1"));
            assertFalse(UpdateChecker.isNewer("0.9.0", "0.8.0"));
        }

        @Test
        @DisplayName("build metadata is ignored")
        void buildMetadataIgnored()
        {
            assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0+build.7"));
        }
    }

    @Nested
    @DisplayName("unusable input")
    class UnusableInput
    {
        @Test
        @DisplayName("never updates from an unknown current version")
        void unknownCurrentNeverUpdates()
        {
            // Running from a directory rather than a jar leaves no version to read. Updating
            // on that basis would replace a build nothing knows anything about.
            assertFalse(UpdateChecker.isNewer("UNKNOWN", "1.0.0"));
        }

        @Test
        @DisplayName("nulls do not trigger an update")
        void nullsDoNotUpdate()
        {
            assertFalse(UpdateChecker.isNewer(null, "1.0.0"));
            assertFalse(UpdateChecker.isNewer("1.0.0", null));
        }
    }
}
