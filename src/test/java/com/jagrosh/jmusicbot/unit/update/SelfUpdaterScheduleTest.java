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

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.update.SelfUpdater;
import com.jagrosh.jmusicbot.update.UpdateChecker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * What {@link SelfUpdater#start()} actually registers on the bot's thread pool — the owner's
 * decision that checking is always on, every hour, and not configurable, made concrete rather
 * than trusted by reading. A previous build also registered a second, more frequent cadence for
 * unattended installing; this fails if anything like that ever comes back, since installing is
 * never automatic any more.
 *
 * <p>Never runs the captured task: doing so would call {@link UpdateChecker#fetchLatest} against
 * the real GitHub API. Checking that {@code start()} schedules the right thing does not require
 * that thing to have actually run.
 */
@DisplayName("SelfUpdater.start()")
class SelfUpdaterScheduleTest
{
    @Test
    @DisplayName("registers exactly one task: the hourly check, five minutes after startup")
    void registersExactlyOneHourlyTask()
    {
        Bot bot = mock(Bot.class);
        ScheduledExecutorService pool = mock(ScheduledExecutorService.class);
        when(bot.getThreadpool()).thenReturn(pool);

        SelfUpdater updater = new SelfUpdater(bot, new UpdateChecker());
        updater.start();

        verify(pool).scheduleWithFixedDelay(any(Runnable.class), eq(5L), eq(60L), eq(TimeUnit.MINUTES));

        // The whole point: nothing else is registered. An older build also scheduled a second,
        // five-minute "install if idle" cadence here — this is what would catch its return.
        verifyNoMoreInteractions(pool);
    }

    @Test
    @DisplayName("calling start() twice registers the task twice, once per call")
    void startIsNotIdempotentByItself()
    {
        // SelfUpdater itself does not guard against being started twice — Bot.setJDA() is what
        // ensures start() is only ever called once, by only constructing one SelfUpdater ever
        // (see the "updater == null" check there). Documented here as the actual behaviour of
        // this class in isolation, so that guarantee is not silently assumed to live in a place
        // it does not.
        Bot bot = mock(Bot.class);
        ScheduledExecutorService pool = mock(ScheduledExecutorService.class);
        when(bot.getThreadpool()).thenReturn(pool);

        SelfUpdater updater = new SelfUpdater(bot, new UpdateChecker());
        updater.start();
        updater.start();

        verify(pool, org.mockito.Mockito.times(2))
                .scheduleWithFixedDelay(any(Runnable.class), eq(5L), eq(60L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("no staged version at construction")
    void nothingStagedAtStart()
    {
        Bot bot = mock(Bot.class);
        SelfUpdater updater = new SelfUpdater(bot, new UpdateChecker());

        assertEquals(java.util.Optional.empty(), updater.getStagedVersion());
    }
}
