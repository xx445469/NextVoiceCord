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
package com.jagrosh.jmusicbot.unit.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PlaylistLoader Tests")
class PlaylistLoaderTest
{
    @TempDir
    Path tempDir;

    private PlaylistLoader loader;
    private AudioPlayerManager manager;

    @BeforeEach
    void setUp()
    {
        BotConfig config = mock(BotConfig.class);
        when(config.getPlaylistsFolder()).thenReturn(tempDir.toString());
        when(config.isTooLong(any(AudioTrack.class))).thenReturn(false);
        loader = new PlaylistLoader(config);
        manager = mock(AudioPlayerManager.class);
    }

    @Test
    @DisplayName("loadTracks() invokes callback only after all items complete")
    void loadTracks_invokesCallbackOnlyAfterAllItemsComplete() throws IOException
    {
        Files.writeString(tempDir.resolve("race.txt"), "url1\nurl2\nurl3\n");
        PlaylistLoader.Playlist playlist = loader.getPlaylist("race");
        assertNotNull(playlist);

        Map<String, AudioLoadResultHandler> handlersByItem = new HashMap<>();
        doAnswer(invocation ->
        {
            String item = invocation.getArgument(1);
            AudioLoadResultHandler handler = invocation.getArgument(2);
            handlersByItem.put(item, handler);
            return null;
        }).when(manager).loadItemOrdered(eq("race"), anyString(), any(AudioLoadResultHandler.class));

        AtomicInteger callbackCount = new AtomicInteger(0);
        List<AudioTrack> consumedTracks = new ArrayList<>();
        playlist.loadTracks(manager, consumedTracks::add, callbackCount::incrementAndGet);

        assertEquals(3, handlersByItem.size());

        AudioTrack track3 = mock(AudioTrack.class);
        handlersByItem.get("url3").trackLoaded(track3);
        assertEquals(0, callbackCount.get());

        AudioTrack track1 = mock(AudioTrack.class);
        handlersByItem.get("url1").trackLoaded(track1);
        assertEquals(0, callbackCount.get());

        handlersByItem.get("url2").noMatches();
        assertEquals(1, callbackCount.get());
        assertEquals(2, consumedTracks.size());
    }

    @Test
    @DisplayName("loadTracks() invokes callback immediately when playlist has no items")
    void loadTracks_invokesCallbackImmediately_whenPlaylistHasNoItems() throws IOException
    {
        Files.writeString(tempDir.resolve("empty.txt"), "\n");
        PlaylistLoader.Playlist playlist = loader.getPlaylist("empty");
        assertNotNull(playlist);

        AtomicInteger callbackCount = new AtomicInteger(0);
        playlist.loadTracks(manager, track -> {}, callbackCount::incrementAndGet);

        assertEquals(1, callbackCount.get());
        verify(manager, never()).loadItemOrdered(anyString(), anyString(), any(AudioLoadResultHandler.class));
    }

    @Test
    @DisplayName("ensureStorageReady() creates nested directories")
    void ensureStorageReady_createsNestedDirectories()
    {
        BotConfig nestedConfig = mock(BotConfig.class);
        when(nestedConfig.getPlaylistsFolder()).thenReturn(tempDir.resolve("a").resolve("b").toString());
        PlaylistLoader nestedLoader = new PlaylistLoader(nestedConfig);

        PlaylistLoader.PlaylistResult<Path> result = nestedLoader.ensureStorageReady();

        assertTrue(result.isSuccess());
        assertTrue(Files.isDirectory(result.getValue()));
    }

    @Test
    @DisplayName("ensureStorageReady() fails for blank path")
    void ensureStorageReady_failsForBlankPath()
    {
        BotConfig invalidConfig = mock(BotConfig.class);
        when(invalidConfig.getPlaylistsFolder()).thenReturn("   ");
        PlaylistLoader invalidLoader = new PlaylistLoader(invalidConfig);

        PlaylistLoader.PlaylistResult<Path> result = invalidLoader.ensureStorageReady();

        assertFalse(result.isSuccess());
        assertEquals(PlaylistLoader.PlaylistErrorType.INVALID_CONFIG, result.getError().getType());
    }

    @Test
    @DisplayName("ensureStorageReady() fails when parent path is file")
    void ensureStorageReady_failsWhenStorageInaccessible() throws IOException
    {
        Path parentFile = tempDir.resolve("not-a-directory");
        Files.writeString(parentFile, "x");

        BotConfig inaccessibleConfig = mock(BotConfig.class);
        when(inaccessibleConfig.getPlaylistsFolder()).thenReturn(parentFile.resolve("child").toString());
        PlaylistLoader inaccessibleLoader = new PlaylistLoader(inaccessibleConfig);

        PlaylistLoader.PlaylistResult<Path> result = inaccessibleLoader.ensureStorageReady();

        assertFalse(result.isSuccess());
        assertEquals(PlaylistLoader.PlaylistErrorType.STORAGE_UNAVAILABLE, result.getError().getType());
    }

    @Test
    @DisplayName("getPlaylistNamesResult() places favorites at the top when present")
    void getPlaylistNamesResult_placesFavoritesFirst() throws IOException
    {
        Files.writeString(tempDir.resolve("chill.txt"), "https://example.com/chill\n");
        Files.writeString(tempDir.resolve("favorites.txt"), "https://example.com/fav\n");
        Files.writeString(tempDir.resolve("workout.txt"), "https://example.com/workout\n");

        PlaylistLoader.PlaylistResult<List<String>> result = loader.getPlaylistNamesResult();

        assertTrue(result.isSuccess());
        assertFalse(result.getValue().isEmpty());
        assertEquals("favorites", result.getValue().get(0));
        assertTrue(result.getValue().containsAll(List.of("favorites", "chill", "workout")));
    }

    @Test
    @DisplayName("appendItemIfAbsentResult() creates favorites file and appends first item")
    void appendItemIfAbsentResult_createsFavoritesAndAppends()
    {
        PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult> result =
                loader.appendItemIfAbsentResult("favorites", "https://example.com/first");

        assertTrue(result.isSuccess());
        assertEquals(PlaylistLoader.AppendIfAbsentStatus.APPENDED, result.getValue().getStatus());
        assertTrue(Files.exists(tempDir.resolve("favorites.txt")));
    }

    @Test
    @DisplayName("appendItemIfAbsentResult() returns already present when duplicate exists")
    void appendItemIfAbsentResult_returnsAlreadyPresentForDuplicate() throws IOException
    {
        Files.writeString(tempDir.resolve("favorites.txt"), "https://example.com/track\n");

        PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult> result =
                loader.appendItemIfAbsentResult("favorites", "https://example.com/track");

        assertTrue(result.isSuccess());
        assertEquals(PlaylistLoader.AppendIfAbsentStatus.ALREADY_PRESENT, result.getValue().getStatus());
        List<String> lines = Files.readAllLines(tempDir.resolve("favorites.txt"));
        assertEquals(1, lines.stream().filter(l -> l.trim().equals("https://example.com/track")).count());
    }

    @Test
    @DisplayName("appendItemIfAbsentResult() refreshes stale favorites cache after external file changes")
    void appendItemIfAbsentResult_refreshesCacheWhenFavoritesFileChanges() throws IOException
    {
        loader.appendItemIfAbsentResult("favorites", "https://example.com/a");
        Files.writeString(tempDir.resolve("favorites.txt"), "https://example.com/a\nhttps://example.com/b\n");

        PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult> result =
                loader.appendItemIfAbsentResult("favorites", "https://example.com/b");

        assertTrue(result.isSuccess());
        assertEquals(PlaylistLoader.AppendIfAbsentStatus.ALREADY_PRESENT, result.getValue().getStatus());
    }

    @Test
    @DisplayName("appendItemIfAbsentResult() uses warm favorites cache for non-duplicate append")
    void appendItemIfAbsentResult_warmCacheAppendsNewEntry() throws IOException
    {
        PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult> first =
                loader.appendItemIfAbsentResult("favorites", "https://example.com/a");
        PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult> second =
                loader.appendItemIfAbsentResult("favorites", "https://example.com/b");

        assertTrue(first.isSuccess());
        assertEquals(PlaylistLoader.AppendIfAbsentStatus.APPENDED, first.getValue().getStatus());
        assertTrue(second.isSuccess());
        assertEquals(PlaylistLoader.AppendIfAbsentStatus.APPENDED, second.getValue().getStatus());

        List<String> lines = Files.readAllLines(tempDir.resolve("favorites.txt"));
        assertEquals(List.of("https://example.com/a", "https://example.com/b"), lines);
    }

    @Test
    @DisplayName("appendItemIfAbsentResult() uses warm favorites cache for duplicate detection")
    void appendItemIfAbsentResult_warmCacheDetectsDuplicate() throws IOException
    {
        loader.appendItemIfAbsentResult("favorites", "https://example.com/a");

        PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult> duplicate =
                loader.appendItemIfAbsentResult("favorites", "https://example.com/a");

        assertTrue(duplicate.isSuccess());
        assertEquals(PlaylistLoader.AppendIfAbsentStatus.ALREADY_PRESENT, duplicate.getValue().getStatus());

        List<String> lines = Files.readAllLines(tempDir.resolve("favorites.txt"));
        assertEquals(1, lines.stream().filter(l -> l.equals("https://example.com/a")).count());
    }

    @Test
    @DisplayName("appendItemIfAbsentResult() reloads stale cache and preserves external additions")
    void appendItemIfAbsentResult_staleCacheAppendsAfterExternalChange() throws IOException
    {
        loader.appendItemIfAbsentResult("favorites", "https://example.com/a");
        Files.writeString(tempDir.resolve("favorites.txt"), "https://example.com/a\nhttps://example.com/external\n");

        PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult> appended =
                loader.appendItemIfAbsentResult("favorites", "https://example.com/b");

        assertTrue(appended.isSuccess());
        assertEquals(PlaylistLoader.AppendIfAbsentStatus.APPENDED, appended.getValue().getStatus());

        List<String> lines = Files.readAllLines(tempDir.resolve("favorites.txt"));
        assertEquals(List.of("https://example.com/a", "https://example.com/external", "https://example.com/b"), lines);
    }

    @Test
    @DisplayName("appendItemIfAbsentResult() is lock-safe under concurrent duplicate appends")
    void appendItemIfAbsentResult_isLockSafeForConcurrentDuplicateAppends() throws Exception
    {
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try
        {
            List<Future<PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult>>> futures = new ArrayList<>();
            for(int i = 0; i < workers; i++)
            {
                futures.add(pool.submit(() ->
                {
                    ready.countDown();
                    start.await();
                    return loader.appendItemIfAbsentResult("favorites", "https://example.com/race");
                }));
            }

            ready.await();
            start.countDown();

            int appendedCount = 0;
            int duplicateCount = 0;
            for(Future<PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult>> future : futures)
            {
                PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult> result = future.get();
                assertTrue(result.isSuccess());
                if(result.getValue().getStatus() == PlaylistLoader.AppendIfAbsentStatus.APPENDED)
                    appendedCount++;
                else
                    duplicateCount++;
            }

            assertEquals(1, appendedCount);
            assertEquals(workers - 1, duplicateCount);
        }
        finally
        {
            pool.shutdownNow();
        }

        List<String> lines = Files.readAllLines(tempDir.resolve("favorites.txt"));
        assertEquals(1, lines.stream().filter(l -> l.trim().equals("https://example.com/race")).count());
    }
}
