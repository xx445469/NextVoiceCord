/*
 * Copyright 2018 John Grosh (jagrosh).
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
package com.jagrosh.jmusicbot.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class PlaylistLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(PlaylistLoader.class);
    private static final String FAVORITES_PLAYLIST_NAME = "favorites";
    private static final byte LF = (byte) '\n';
    private static final byte CR = (byte) '\r';

    public enum PlaylistErrorType
    {
        INVALID_CONFIG,
        STORAGE_UNAVAILABLE,
        PLAYLIST_NOT_FOUND
    }

    public static final class PlaylistError
    {
        private final PlaylistErrorType type;
        private final String message;
        private final String configuredPath;
        private final Throwable cause;

        private PlaylistError(PlaylistErrorType type, String message, String configuredPath, Throwable cause)
        {
            this.type = type;
            this.message = message;
            this.configuredPath = configuredPath;
            this.cause = cause;
        }

        public static PlaylistError of(PlaylistErrorType type, String message, String configuredPath, Throwable cause)
        {
            return new PlaylistError(type, message, configuredPath, cause);
        }

        public PlaylistErrorType getType()
        {
            return type;
        }

        public String getMessage()
        {
            return message;
        }

        public String getConfiguredPath()
        {
            return configuredPath;
        }

        public Throwable getCause()
        {
            return cause;
        }
    }

    public static final class PlaylistResult<T>
    {
        private final T value;
        private final PlaylistError error;

        private PlaylistResult(T value, PlaylistError error)
        {
            this.value = value;
            this.error = error;
        }

        public static <T> PlaylistResult<T> success(T value)
        {
            return new PlaylistResult<>(value, null);
        }

        public static <T> PlaylistResult<T> failure(PlaylistError error)
        {
            return new PlaylistResult<>(null, error);
        }

        public boolean isSuccess()
        {
            return error == null;
        }

        public T getValue()
        {
            return value;
        }

        public PlaylistError getError()
        {
            return error;
        }
    }

    public enum AppendIfAbsentStatus
    {
        APPENDED,
        ALREADY_PRESENT
    }

    public static final class AppendIfAbsentResult
    {
        private final AppendIfAbsentStatus status;

        private AppendIfAbsentResult(AppendIfAbsentStatus status)
        {
            this.status = status;
        }

        public static AppendIfAbsentResult appended()
        {
            return new AppendIfAbsentResult(AppendIfAbsentStatus.APPENDED);
        }

        public static AppendIfAbsentResult alreadyPresent()
        {
            return new AppendIfAbsentResult(AppendIfAbsentStatus.ALREADY_PRESENT);
        }

        public AppendIfAbsentStatus getStatus()
        {
            return status;
        }
    }

    private static final class FileSignature
    {
        private final boolean exists;
        private final long size;
        private final long lastModifiedMillis;

        private FileSignature(boolean exists, long size, long lastModifiedMillis)
        {
            this.exists = exists;
            this.size = size;
            this.lastModifiedMillis = lastModifiedMillis;
        }

        private static FileSignature missing()
        {
            return new FileSignature(false, 0L, 0L);
        }

        private static FileSignature of(Path path) throws IOException
        {
            if(!Files.exists(path))
                return missing();
            FileTime lastModified = Files.getLastModifiedTime(path);
            return new FileSignature(true, Files.size(path), lastModified.toMillis());
        }

        @Override
        public boolean equals(Object other)
        {
            if(this == other)
                return true;
            if(!(other instanceof FileSignature that))
                return false;
            return exists == that.exists
                    && size == that.size
                    && lastModifiedMillis == that.lastModifiedMillis;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(exists, size, lastModifiedMillis);
        }
    }

    private static final class FavoritesCache
    {
        private final FileSignature signature;
        private final Set<String> entries;

        private FavoritesCache(FileSignature signature, Set<String> entries)
        {
            this.signature = signature;
            this.entries = entries;
        }
    }

    private final BotConfig config;
    private volatile PlaylistError lastStorageError;
    private volatile FavoritesCache favoritesCache;
    private final Map<Path, Object> inProcessFileLocks = new ConcurrentHashMap<>();
    
    public PlaylistLoader(BotConfig config)
    {
        this.config = config;
    }
    
    public PlaylistResult<Path> ensureStorageReady()
    {
        return ensureStorageReady(true);
    }

    public PlaylistResult<Path> checkStorageReady()
    {
        return ensureStorageReady(false);
    }

    private PlaylistResult<Path> ensureStorageReady(boolean createIfMissing)
    {
        String configuredPath = config.getPlaylistsFolder();
        if(configuredPath == null || configuredPath.trim().isEmpty())
        {
            return rememberError(PlaylistErrorType.INVALID_CONFIG, "Playlists folder is not configured.", configuredPath, null);
        }

        Path folderPath;
        try
        {
            folderPath = OtherUtil.getPath(configuredPath).toAbsolutePath().normalize();
        }
        catch(Exception ex)
        {
            return rememberError(PlaylistErrorType.INVALID_CONFIG,
                    "Playlists folder path is invalid: " + ex.getMessage(), configuredPath, ex);
        }

        try
        {
            if(createIfMissing)
            {
                Files.createDirectories(folderPath);
            }
            else if(!Files.exists(folderPath))
            {
                return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                        "Playlists directory does not exist and could not be accessed.", configuredPath, null);
            }

            if(!Files.isDirectory(folderPath))
            {
                return rememberError(PlaylistErrorType.INVALID_CONFIG,
                        "Configured playlists path is not a directory.", configuredPath, null);
            }
            if(!Files.isReadable(folderPath) || !Files.isWritable(folderPath))
            {
                return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                        "Playlists directory is not readable and writable.", configuredPath, null);
            }

            lastStorageError = null;
            return PlaylistResult.success(folderPath);
        }
        catch(IOException ex)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to access playlists directory: " + ex.getMessage(), configuredPath, ex);
        }
    }

    public PlaylistResult<List<String>> getPlaylistNamesResult()
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());

        File folder = readiness.getValue().toFile();
        File[] files = folder.listFiles((pathname) -> pathname.getName().endsWith(".txt"));
        if(files == null)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to list playlists directory contents.", config.getPlaylistsFolder(), null);
        }

        List<String> favoriteNames = new ArrayList<>(1);
        List<String> otherNames = new ArrayList<>(files.length);
        for (File file : files)
        {
            String name = file.getName().substring(0, file.getName().length() - 4);
            if (name.equalsIgnoreCase(FAVORITES_PLAYLIST_NAME))
            {
                favoriteNames.add(name);
            }
            else
            {
                otherNames.add(name);
            }
        }

        List<String> orderedNames = new ArrayList<>(favoriteNames.size() + otherNames.size());
        orderedNames.addAll(favoriteNames);
        orderedNames.addAll(otherNames);
        return PlaylistResult.success(orderedNames);
    }

    public List<String> getPlaylistNames()
    {
        PlaylistResult<List<String>> result = getPlaylistNamesResult();
        return result.isSuccess() ? result.getValue() : null;
    }

    public void createFolder()
    {
        ensureStorageReady();
    }
    
    public boolean folderExists()
    {
        String path = config.getPlaylistsFolder();
        if(path == null || path.trim().isEmpty())
            return false;
        try
        {
            return Files.isDirectory(OtherUtil.getPath(path));
        }
        catch(Exception ex)
        {
            return false;
        }
    }
    
    public void createPlaylist(String name) throws IOException
    {
        PlaylistResult<Void> result = createPlaylistResult(name);
        if(!result.isSuccess())
            throw toIOException(result.getError());
    }
    
    public void deletePlaylist(String name) throws IOException
    {
        PlaylistResult<Void> result = deletePlaylistResult(name);
        if(!result.isSuccess())
            throw toIOException(result.getError());
    }
    
    public void writePlaylist(String name, String text) throws IOException
    {
        PlaylistResult<Void> result = writePlaylistResult(name, text);
        if(!result.isSuccess())
            throw toIOException(result.getError());
    }

    public PlaylistResult<Void> createPlaylistResult(String name)
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());
        try
        {
            Files.createFile(playlistPath(readiness.getValue(), name));
            invalidateFavoritesCacheIfNeeded(name);
            return PlaylistResult.success(null);
        }
        catch(IOException ex)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to create playlist `" + name + "`: " + ex.getMessage(), config.getPlaylistsFolder(), ex);
        }
    }

    public PlaylistResult<Void> deletePlaylistResult(String name)
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());
        Path path = playlistPath(readiness.getValue(), name);
        if(!Files.exists(path))
        {
            return PlaylistResult.failure(new PlaylistError(PlaylistErrorType.PLAYLIST_NOT_FOUND,
                    "Playlist `" + name + "` does not exist.", config.getPlaylistsFolder(), null));
        }
        try
        {
            Files.delete(path);
            invalidateFavoritesCacheIfNeeded(name);
            return PlaylistResult.success(null);
        }
        catch(IOException ex)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to delete playlist `" + name + "`: " + ex.getMessage(), config.getPlaylistsFolder(), ex);
        }
    }

    public PlaylistResult<Void> writePlaylistResult(String name, String text)
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());

        Path path = playlistPath(readiness.getValue(), name);
        if(!Files.exists(path))
        {
            return PlaylistResult.failure(new PlaylistError(PlaylistErrorType.PLAYLIST_NOT_FOUND,
                    "Playlist `" + name + "` does not exist.", config.getPlaylistsFolder(), null));
        }
        try
        {
            byte[] content = text.trim().getBytes(StandardCharsets.UTF_8);
            Files.write(path, content);
            refreshOrInvalidateFavoritesCacheAfterWrite(name, path, content);
            return PlaylistResult.success(null);
        }
        catch(IOException ex)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to write playlist `" + name + "`: " + ex.getMessage(), config.getPlaylistsFolder(), ex);
        }
    }
    
    public Playlist getPlaylist(String name)
    {
        PlaylistResult<Playlist> result = getPlaylistResult(name);
        return result.isSuccess() ? result.getValue() : null;
    }

    public PlaylistResult<Playlist> getPlaylistResult(String name)
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());
        Path path = playlistPath(readiness.getValue(), name);
        if(!Files.exists(path))
        {
            return PlaylistResult.failure(new PlaylistError(PlaylistErrorType.PLAYLIST_NOT_FOUND,
                    "Playlist `" + name + "` does not exist.", config.getPlaylistsFolder(), null));
        }
        try
        {
            ParsedPlaylist parsed = parsePlaylistLines(Files.readAllLines(path), true);
            List<String> list = parsed.items;
            boolean shuffle = parsed.shuffle;
            if(shuffle)
                shuffle(list);
            return PlaylistResult.success(new Playlist(name, list, shuffle));
        }
        catch(IOException e)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to read playlist `" + name + "`: " + e.getMessage(), config.getPlaylistsFolder(), e);
        }
    }

    public PlaylistResult<AppendIfAbsentResult> appendItemIfAbsentResult(String name, String item)
    {
        PlaylistResult<Path> readiness = ensureStorageReady();
        if(!readiness.isSuccess())
            return PlaylistResult.failure(readiness.getError());

        String normalizedItem = item == null ? "" : item.trim();
        if(normalizedItem.isEmpty())
        {
            return rememberError(PlaylistErrorType.INVALID_CONFIG,
                    "Cannot append an empty playlist item.", config.getPlaylistsFolder(), null);
        }

        Path path = playlistPath(readiness.getValue(), name);
        boolean favoritesPlaylist = isFavoritesPlaylist(name);
        try
        {
            if(favoritesPlaylist && isFavoritesCacheHit(path, normalizedItem))
            {
                LOG.debug("Favorites append fast-path duplicate hit (pre-lock): entry={}", normalizedItem);
                return PlaylistResult.success(AppendIfAbsentResult.alreadyPresent());
            }

            Set<String> itemsAfterOperation;
            AppendIfAbsentResult operationResult;
            Path lockKey = path.toAbsolutePath().normalize();
            Object inProcessLock = inProcessFileLocks.computeIfAbsent(lockKey, ignored -> new Object());
            synchronized(inProcessLock)
            {
                try(FileChannel channel = FileChannel.open(path,
                        StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                    FileLock lock = channel.lock())
                {
                    if(!lock.isValid())
                    {
                        throw new IOException("Failed to acquire a valid lock for playlist file.");
                    }

                    CacheAppendResult cacheAppendResult = favoritesPlaylist
                            ? tryAppendWithFreshFavoritesCache(path, channel, normalizedItem)
                            : null;
                    if(cacheAppendResult != null)
                    {
                        LOG.debug("Favorites append lock-path used warm cache: result={}, entry={}",
                                cacheAppendResult.result.getStatus(), normalizedItem);
                        itemsAfterOperation = cacheAppendResult.entries;
                        operationResult = cacheAppendResult.result;
                    }
                    else
                    {
                        if(favoritesPlaylist)
                        {
                            LOG.debug("Favorites append cache unavailable/stale under lock; reading full file for authoritative check.");
                        }
                        Set<String> entries = readPlaylistItemsFromChannel(channel);
                        if(entries.contains(normalizedItem))
                        {
                            itemsAfterOperation = entries;
                            operationResult = AppendIfAbsentResult.alreadyPresent();
                        }
                        else
                        {
                            appendLine(channel, normalizedItem);
                            entries.add(normalizedItem);
                            itemsAfterOperation = entries;
                            operationResult = AppendIfAbsentResult.appended();
                        }
                    }
                }
            }
            if(favoritesPlaylist)
                refreshFavoritesCache(path, itemsAfterOperation);
            return PlaylistResult.success(operationResult);
        }
        catch(IOException ex)
        {
            return rememberError(PlaylistErrorType.STORAGE_UNAVAILABLE,
                    "Failed to append item to playlist `" + name + "`: " + ex.getMessage(),
                    config.getPlaylistsFolder(), ex);
        }
    }

    public Optional<PlaylistError> getLastStorageError()
    {
        return Optional.ofNullable(lastStorageError);
    }

    private <T> PlaylistResult<T> rememberError(PlaylistErrorType type, String message, String configuredPath, Throwable cause)
    {
        PlaylistError error = PlaylistError.of(type, message, configuredPath, cause);
        if(type != PlaylistErrorType.PLAYLIST_NOT_FOUND)
        {
            lastStorageError = error;
            if(cause == null)
                LOG.warn("Playlist storage issue ({}): path={}, message={}", type, configuredPath, message);
            else
                LOG.warn("Playlist storage issue ({}): path={}, message={}, cause={}",
                        type, configuredPath, message, cause.getMessage());
        }
        return PlaylistResult.failure(error);
    }

    private Path playlistPath(Path folder, String name)
    {
        return folder.resolve(name + ".txt");
    }

    private void appendLine(FileChannel channel, String value) throws IOException
    {
        long size = channel.size();
        channel.position(size);
        if(size > 0)
        {
            ByteBuffer last = ByteBuffer.allocate(1);
            channel.position(size - 1);
            channel.read(last);
            last.flip();
            byte lastByte = last.get();
            channel.position(size);
            if(lastByte == CR)
                channel.write(ByteBuffer.wrap(new byte[]{LF}));
            else if(lastByte != LF)
                channel.write(ByteBuffer.wrap(new byte[]{CR, LF}));
        }
        channel.write(ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8)));
    }

    private Set<String> readPlaylistItemsFromChannel(FileChannel channel) throws IOException
    {
        long size = channel.size();
        if(size <= 0)
            return new LinkedHashSet<>();
        if(size > Integer.MAX_VALUE)
        {
            throw new IOException("Playlist file is too large to process in memory: " + size + " bytes");
        }

        channel.position(0);
        ByteBuffer bytes = ByteBuffer.allocate((int) size);
        while(bytes.hasRemaining() && channel.read(bytes) != -1)
        {
            // read until full buffer
        }
        bytes.flip();
        String content = StandardCharsets.UTF_8.decode(bytes).toString();
        List<String> lines = Arrays.asList(content.split("\\R"));
        ParsedPlaylist parsed = parsePlaylistLines(lines, false);
        return new LinkedHashSet<>(parsed.items);
    }

    private CacheAppendResult tryAppendWithFreshFavoritesCache(Path path, FileChannel channel, String item) throws IOException
    {
        FavoritesCache cacheSnapshot = favoritesCache;
        if(cacheSnapshot == null)
        {
            LOG.debug("Favorites append lock-path cache miss: no cache snapshot.");
            return null;
        }

        FileSignature currentSignature = FileSignature.of(path);
        if(!cacheSnapshot.signature.equals(currentSignature))
        {
            LOG.debug("Favorites append lock-path cache stale: signature mismatch.");
            return null;
        }

        Set<String> entries = new LinkedHashSet<>(cacheSnapshot.entries);
        if(entries.contains(item))
        {
            return new CacheAppendResult(entries, AppendIfAbsentResult.alreadyPresent());
        }

        appendLine(channel, item);
        entries.add(item);
        return new CacheAppendResult(entries, AppendIfAbsentResult.appended());
    }

    private boolean isFavoritesCacheHit(Path favoritesPath, String item) throws IOException
    {
        FavoritesCache cacheSnapshot = favoritesCache;
        if(cacheSnapshot == null)
            return false;

        FileSignature currentSignature = FileSignature.of(favoritesPath);
        if(!cacheSnapshot.signature.equals(currentSignature))
            return false;
        return cacheSnapshot.entries.contains(item);
    }

    private ParsedPlaylist parsePlaylistLines(List<String> rawLines, boolean includeShuffleDirective)
    {
        boolean shuffle = false;
        List<String> items = new ArrayList<>();
        for(String line : rawLines)
        {
            String value = line.trim();
            if(value.isEmpty())
                continue;
            if(value.startsWith("#") || value.startsWith("//"))
            {
                if(includeShuffleDirective)
                {
                    String compact = value.replaceAll("\\s+", "");
                    if(compact.equalsIgnoreCase("#shuffle") || compact.equalsIgnoreCase("//shuffle"))
                        shuffle = true;
                }
                continue;
            }
            items.add(value);
        }
        return new ParsedPlaylist(items, shuffle);
    }

    private void refreshFavoritesCache(Path path, Set<String> entries) throws IOException
    {
        if(!Files.exists(path))
        {
            favoritesCache = new FavoritesCache(FileSignature.missing(), Collections.emptySet());
            LOG.debug("Favorites cache refreshed to empty snapshot because favorites file is missing.");
            return;
        }
        FileSignature signature = FileSignature.of(path);
        Set<String> snapshot = Collections.unmodifiableSet(new LinkedHashSet<>(entries));
        favoritesCache = new FavoritesCache(signature, snapshot);
        LOG.debug("Favorites cache refreshed: entries={}, size={}, lastModifiedMillis={}",
                snapshot.size(), signature.size, signature.lastModifiedMillis);
    }

    private void refreshOrInvalidateFavoritesCacheAfterWrite(String playlistName, Path path, byte[] content) throws IOException
    {
        if(!isFavoritesPlaylist(playlistName))
            return;
        if(content.length == 0)
        {
            favoritesCache = new FavoritesCache(FileSignature.of(path), Collections.emptySet());
            return;
        }

        List<String> lines = Arrays.asList(new String(content, StandardCharsets.UTF_8).split("\\R"));
        ParsedPlaylist parsed = parsePlaylistLines(lines, false);
        refreshFavoritesCache(path, new LinkedHashSet<>(parsed.items));
    }

    private void invalidateFavoritesCacheIfNeeded(String playlistName)
    {
        if(isFavoritesPlaylist(playlistName))
        {
            favoritesCache = null;
            LOG.debug("Favorites cache invalidated due to playlist structural change: {}", playlistName);
        }
    }

    private boolean isFavoritesPlaylist(String name)
    {
        return FAVORITES_PLAYLIST_NAME.equalsIgnoreCase(name);
    }

    private static final class ParsedPlaylist
    {
        private final List<String> items;
        private final boolean shuffle;

        private ParsedPlaylist(List<String> items, boolean shuffle)
        {
            this.items = items;
            this.shuffle = shuffle;
        }
    }

    private static final class CacheAppendResult
    {
        private final Set<String> entries;
        private final AppendIfAbsentResult result;

        private CacheAppendResult(Set<String> entries, AppendIfAbsentResult result)
        {
            this.entries = entries;
            this.result = result;
        }
    }

    private IOException toIOException(PlaylistError error)
    {
        if(error.getCause() instanceof IOException ioEx)
            return ioEx;
        return new IOException(error.getMessage(), error.getCause());
    }
    
    
    private static <T> void shuffle(List<T> list)
    {
        for(int first =0; first<list.size(); first++)
        {
            int second = (int)(Math.random()*list.size());
            T tmp = list.get(first);
            list.set(first, list.get(second));
            list.set(second, tmp);
        }
    }
    
    
    public class Playlist
    {
        private final String name;
        private final List<String> items;
        private final boolean shuffle;
        private final List<AudioTrack> tracks = new LinkedList<>();
        private final List<PlaylistLoadError> errors = new LinkedList<>();
        private boolean loaded = false;
        
        private Playlist(String name, List<String> items, boolean shuffle)
        {
            this.name = name;
            this.items = items;
            this.shuffle = shuffle;
        }
        
        public void loadTracks(AudioPlayerManager manager, Consumer<AudioTrack> consumer, Runnable callback)
        {
            if(loaded)
                return;
            loaded = true;
            if(items.isEmpty())
            {
                if(callback != null)
                    callback.run();
                return;
            }
            AtomicInteger pendingItems = new AtomicInteger(items.size());
            for(int i=0; i<items.size(); i++)
            {
                int index = i;
                manager.loadItemOrdered(name, items.get(i), new AudioLoadResultHandler() 
                {
                    private void done()
                    {
                        if(pendingItems.decrementAndGet() == 0)
                        {
                            if(shuffle)
                                shuffleTracks();
                            if(callback != null)
                                callback.run();
                        }
                    }

                    @Override
                    public void trackLoaded(AudioTrack at) 
                    {
                        if(config.isTooLong(at))
                            errors.add(new PlaylistLoadError(index, items.get(index), "This track is longer than the allowed maximum"));
                        else
                        {
                            at.setUserData(0L);
                            tracks.add(at);
                            consumer.accept(at);
                        }
                        done();
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist ap) 
                    {
                        if(ap.isSearchResult())
                        {
                            trackLoaded(ap.getTracks().get(0));
                        }
                        else if(ap.getSelectedTrack()!=null)
                        {
                            trackLoaded(ap.getSelectedTrack());
                        }
                        else
                        {
                            List<AudioTrack> loaded = new ArrayList<>(ap.getTracks());
                            if(shuffle)
                                for(int first =0; first<loaded.size(); first++)
                                {
                                    int second = (int)(Math.random()*loaded.size());
                                    AudioTrack tmp = loaded.get(first);
                                    loaded.set(first, loaded.get(second));
                                    loaded.set(second, tmp);
                                }
                            loaded.removeIf(track -> config.isTooLong(track));
                            loaded.forEach(at -> at.setUserData(0L));
                            tracks.addAll(loaded);
                            loaded.forEach(at -> consumer.accept(at));
                        }
                        done();
                    }

                    @Override
                    public void noMatches() 
                    {
                        errors.add(new PlaylistLoadError(index, items.get(index), "No matches found."));
                        done();
                    }

                    @Override
                    public void loadFailed(FriendlyException fe) 
                    {
                        errors.add(new PlaylistLoadError(index, items.get(index), "Failed to load track: "+fe.getLocalizedMessage()));
                        done();
                    }
                });
            }
        }
        
        public void shuffleTracks()
        {
            shuffle(tracks);
        }
        
        public String getName()
        {
            return name;
        }

        public List<String> getItems()
        {
            return items;
        }

        public List<AudioTrack> getTracks()
        {
            return tracks;
        }
        
        public List<PlaylistLoadError> getErrors()
        {
            return errors;
        }
    }
    
    public class PlaylistLoadError
    {
        private final int number;
        private final String item;
        private final String reason;
        
        private PlaylistLoadError(int number, String item, String reason)
        {
            this.number = number;
            this.item = item;
            this.reason = reason;
        }
        
        public int getIndex()
        {
            return number;
        }
        
        public String getItem()
        {
            return item;
        }
        
        public String getReason()
        {
            return reason;
        }
    }
}
