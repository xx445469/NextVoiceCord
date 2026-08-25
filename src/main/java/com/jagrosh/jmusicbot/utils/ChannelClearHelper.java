/*
 * Copyright 2026 Arif Banai (arif-banai)
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

import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.Permission;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared logic for clearing all messages in the guild's configured text channel.
 * Used by both the slash and prefix clearchannel commands.
 */
public final class ChannelClearHelper
{
    private static final int HISTORY_BATCH_SIZE = 100;
    private static final long DISCORD_BULK_DELETE_MAX_AGE_DAYS = 14;

    private ChannelClearHelper() {}

    /**
     * Callbacks for clear-channel operations. Implement to send replies (slash or prefix).
     */
    public interface ClearChannelCallback
    {
        void onNoChannelConfigured();

        void onNoPermission();

        void onClearingStarted();

        void onCleared(int count);

        void onError(Throwable t);
    }

    /**
     * Callback for purge-only operations (after channel is already resolved).
     */
    public interface PurgeCallback
    {
        void onCleared(int count);

        void onError(Throwable t);
    }

    public record ClearPolicy(int deleteLimit, long ageDays)
    {
        public static ClearPolicy of(int deleteLimit, long ageDays)
        {
            return new ClearPolicy(Math.max(0, deleteLimit), Math.max(0L, ageDays));
        }
    }

    private record ClearSweepState(Instant startedAt, String beforeMessageId) {}

    /**
     * Runs the purge on the given channel. Use this after deferring a slash reply.
     *
     * @param channel  the channel to clear
     * @param callback onCleared(count) or onError(t) when done
     * @param policy   delete policy for max count and age window
     */
    public static void purgeChannel(TextChannel channel, PurgeCallback callback, ClearPolicy policy)
    {
        AtomicInteger totalDeleted = new AtomicInteger(0);
        ClearSweepState sweepState = new ClearSweepState(Instant.now(), null);
        takeAndProcessBatch(channel, totalDeleted, policy, sweepState, new ClearChannelCallback()
        {
            @Override
            public void onNoChannelConfigured() { /* not used */ }

            @Override
            public void onNoPermission() { /* not used */ }

            @Override
            public void onClearingStarted() { /* not used */ }

            @Override
            public void onCleared(int count)
            {
                callback.onCleared(count);
            }

            @Override
            public void onError(Throwable t)
            {
                callback.onError(t);
            }
        });
    }

    /**
     * Resolves the target channel from settings, checks permissions, then runs the purge asynchronously.
     * Callbacks are invoked on the calling thread for validation errors; onClearingStarted is called
     * before starting the purge, then onCleared(count) or onError(t) when done.
     *
     * @param guild    the guild
     * @param settings the guild settings (e.g. from event.getClient().getSettingsFor(guild))
     * @param policy   delete policy for max count and age window
     * @param callback callbacks for results and progress
     */
    public static void clearConfiguredTextChannel(Guild guild, Settings settings, ClearPolicy policy, ClearChannelCallback callback)
    {
        TextChannel channel = settings.getTextChannel(guild);
        if (channel == null)
        {
            callback.onNoChannelConfigured();
            return;
        }

        if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE))
        {
            callback.onNoPermission();
            return;
        }

        callback.onClearingStarted();

        purgeChannel(channel, new PurgeCallback()
        {
            @Override
            public void onCleared(int count)
            {
                callback.onCleared(count);
            }

            @Override
            public void onError(Throwable t)
            {
                callback.onError(t);
            }
        }, policy);
    }

    private static void takeAndProcessBatch(TextChannel channel, AtomicInteger totalDeleted, ClearPolicy policy,
                                            ClearSweepState sweepState,
                                            ClearChannelCallback callback)
    {
        int remaining = policy.deleteLimit() > 0
                ? policy.deleteLimit() - totalDeleted.get()
                : HISTORY_BATCH_SIZE;
        if (policy.deleteLimit() > 0 && remaining <= 0)
        {
            callback.onCleared(totalDeleted.get());
            return;
        }
        int requestSize = Math.min(HISTORY_BATCH_SIZE, Math.max(1, remaining));

        loadBatch(channel, sweepState.beforeMessageId(), requestSize, (batch, throwable) ->
        {
            if (throwable != null)
            {
                if (isRateLimit(throwable))
                    callback.onCleared(totalDeleted.get());
                else
                    callback.onError(throwable);
                return;
            }
            if (batch == null || batch.isEmpty())
            {
                callback.onCleared(totalDeleted.get());
                return;
            }

            String nextBeforeMessageId = batch.get(batch.size() - 1).getId();
            ClearSweepState nextSweepState = new ClearSweepState(sweepState.startedAt(), nextBeforeMessageId);

            Instant policyCutoff = policy.ageDays() > 0
                    ? Instant.now().minus(policy.ageDays(), ChronoUnit.DAYS)
                    : null;
            final Instant bulkCutoff = Instant.now().minus(DISCORD_BULK_DELETE_MAX_AGE_DAYS, ChronoUnit.DAYS);
            List<Message> eligible = new ArrayList<>();
            for (Message m : batch)
            {
                Instant createdAt = m.getTimeCreated().toInstant();
                // Only delete messages that existed when the command started.
                if (createdAt.isAfter(sweepState.startedAt()))
                    continue;
                if (policyCutoff == null || createdAt.isAfter(policyCutoff))
                    eligible.add(m);
            }

            if (eligible.isEmpty())
            {
                if (batch.size() >= requestSize)
                    takeAndProcessBatch(channel, totalDeleted, policy, nextSweepState, callback);
                else
                    callback.onCleared(totalDeleted.get());
                return;
            }

            List<Message> recent = new ArrayList<>();
            List<Message> old = new ArrayList<>();
            for (Message m : eligible)
            {
                if (m.getTimeCreated().toInstant().isAfter(bulkCutoff))
                    recent.add(m);
                else
                    old.add(m);
            }

            Runnable onBatchDone = () ->
            {
                int total = totalDeleted.get();
                boolean hasLimit = policy.deleteLimit() > 0;
                if (hasLimit && total >= policy.deleteLimit())
                {
                    callback.onCleared(total);
                    return;
                }
                if (batch.size() >= requestSize)
                    takeAndProcessBatch(channel, totalDeleted, policy, nextSweepState, callback);
                else
                    callback.onCleared(total);
            };

            if (recent.isEmpty() && old.isEmpty())
            {
                onBatchDone.run();
                return;
            }

            if (!recent.isEmpty())
            {
                List<CompletableFuture<Void>> purgeFutures = channel.purgeMessages(recent);
                CompletableFuture.allOf(purgeFutures.toArray(new CompletableFuture[0]))
                        .thenRun(() ->
                        {
                            totalDeleted.addAndGet(recent.size());
                            deleteOldOneByOne(old, 0, totalDeleted, callback, onBatchDone);
                        })
                        .exceptionally(t ->
                        {
                            if (isRateLimit(t))
                                callback.onCleared(totalDeleted.get());
                            else
                                callback.onError(t);
                            return null;
                        });
            }
            else
                deleteOldOneByOne(old, 0, totalDeleted, callback, onBatchDone);
        });
    }

    private static void loadBatch(TextChannel channel, String beforeMessageId, int requestSize,
                                  java.util.function.BiConsumer<List<Message>, Throwable> callback)
    {
        if (beforeMessageId == null)
        {
            channel.getHistory().retrievePast(requestSize).queue(
                    messages -> callback.accept(messages, null),
                    t -> callback.accept(null, t)
            );
            return;
        }

        channel.getHistoryBefore(beforeMessageId, requestSize).queue(
                history -> callback.accept(history.getRetrievedHistory(), null),
                t -> callback.accept(null, t)
        );
    }

    private static void deleteOldOneByOne(List<Message> old, int index, AtomicInteger totalDeleted,
                                          ClearChannelCallback callback, Runnable whenDone)
    {
        if (index >= old.size())
        {
            whenDone.run();
            return;
        }
        Message msg = old.get(index);
        msg.delete().queue(
                v ->
                {
                    totalDeleted.incrementAndGet();
                    deleteOldOneByOne(old, index + 1, totalDeleted, callback, whenDone);
                },
                t ->
                {
                    if (isRateLimit(t))
                        callback.onCleared(totalDeleted.get());
                    else
                        callback.onError(t);
                }
        );
    }

    private static boolean isRateLimit(Throwable throwable)
    {
        Throwable current = throwable;
        while (current != null)
        {
            if (current instanceof RateLimitedException)
                return true;
            if (current instanceof ErrorResponseException ex && ex.getErrorResponse().name().contains("RATE"))
                return true;
            current = current.getCause();
        }
        return false;
    }
}
