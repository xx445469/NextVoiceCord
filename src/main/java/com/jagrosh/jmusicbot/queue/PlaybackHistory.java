package com.jagrosh.jmusicbot.queue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A bounded LIFO buffer for tracking playback history.
 * Most recently played tracks are stored at the front (index 0) and are the first to be removed.
 * <p>
 * Backed by an {@link IndexedDeque} for O(1) add/remove at both ends and O(1) access by index.
 * When a key extractor is provided, at most one entry per key is kept; adding an item with an
 * existing key removes the old entry first (de-dup at add time).
 *
 * @author Arif Banai
 * @param <T> The type of items to store in history
 */
public class PlaybackHistory<T> {
    private final IndexedDeque<T> history;
    private final Function<T, Object> keyExtractor;
    private final Set<Object> keySet;
    private int maxSize;

    /**
     * Creates a new PlaybackHistory with the specified maximum size and no key-based de-dup.
     *
     * @param maxSize The maximum number of items to keep in history (must be >= 0; 0 disables history storage)
     * @throws IllegalArgumentException if maxSize is negative
     */
    public PlaybackHistory(int maxSize) {
        this(maxSize, null);
    }

    /**
     * Creates a new PlaybackHistory with the specified maximum size and optional key extractor.
     * When keyExtractor is non-null, adding an item will first remove any existing entry with
     * the same key, so at most one entry per key is kept (most recent at index 0).
     *
     * @param maxSize The maximum number of items to keep in history (must be >= 0; 0 disables history storage)
     * @param keyExtractor Extracts the key for de-dup; null to disable key-based de-dup
     * @throws IllegalArgumentException if maxSize is negative
     */
    public PlaybackHistory(int maxSize, Function<T, Object> keyExtractor) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("Max size must be non-negative, got: " + maxSize);
        }
        this.maxSize = maxSize;
        this.history = new IndexedDeque<>(Math.max(1, maxSize));
        this.keyExtractor = keyExtractor;
        this.keySet = keyExtractor != null ? new HashSet<>() : null;
    }

    /**
     * Adds an item to the history. The item is added at the front (most recent).
     * When a key extractor is set, any existing entry with the same key is removed first (de-dup).
     * If the history is at max size, the oldest item is removed first to avoid resize.
     *
     * @param item The item to add to history
     * @throws NullPointerException if item is null
     */
    public void add(T item) {
        Objects.requireNonNull(item, "item");
        if (maxSize == 0) {
            return;
        }
        if (keyExtractor != null) {
            removeByKey(keyExtractor.apply(item));
        }
        if (history.size() >= maxSize) {
            T removed = history.removeLast();
            if (keySet != null && removed != null) {
                keySet.remove(keyExtractor.apply(removed));
            }
        }
        history.addFirst(item);
        if (keySet != null) {
            keySet.add(keyExtractor.apply(item));
        }
    }

    /**
     * Removes and returns the most recently added item (from the front).
     * This is used for rewinding to previous tracks.
     *
     * @return The most recently added item, or null if history is empty
     */
    public T removeFirst() {
        T removed = history.pollFirst();
        if (removed != null && keySet != null) {
            keySet.remove(keyExtractor.apply(removed));
        }
        return removed;
    }

    /**
     * Sets the maximum number of items to keep in history.
     * If the current history size exceeds the new max size, oldest items are removed.
     *
     * @param size The maximum number of items to keep (must be >= 0; 0 disables history storage)
     * @throws IllegalArgumentException if size is negative
     */
    public void setMaxSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Max size must be non-negative, got: " + size);
        }
        this.maxSize = size;
        while (history.size() > maxSize) {
            T removed = history.removeLast();
            if (keySet != null && removed != null) {
                keySet.remove(keyExtractor.apply(removed));
            }
        }
    }

    /**
     * Gets the maximum number of items this history can hold.
     *
     * @return The maximum size
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Clears all items from the history.
     */
    public void clear() {
        history.clear();
        if (keySet != null) {
            keySet.clear();
        }
    }

    /**
     * Gets the current number of items in history.
     *
     * @return The current size
     */
    public int size() {
        return history.size();
    }

    /**
     * Checks if the history is empty.
     *
     * @return true if history is empty, false otherwise
     */
    public boolean isEmpty() {
        return history.isEmpty();
    }

    /**
     * Gets an unmodifiable view of the history list.
     * Most recent items are at index 0, oldest at the end.
     *
     * @return An unmodifiable list of history items
     */
    public List<T> getList() {
        return List.copyOf(history);
    }

    /**
     * Gets the item at the specified index.
     * Index 0 is the most recent item. O(1).
     *
     * @param index The index (0 = most recent)
     * @return The item at that index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public T get(int index) {
        return history.get(index);
    }

    /**
     * Removes the item at the specified index (0 = most recent).
     * Used when replaying a track from history so it is not retained in history.
     *
     * @param index The index to remove (0 = most recent)
     * @return The removed item, or null if index is out of range
     */
    public T removeAt(int index) {
        if (index < 0 || index >= history.size()) {
            return null;
        }
        T removed = history.removeAt(index);
        if (removed != null && keySet != null) {
            keySet.remove(keyExtractor.apply(removed));
        }
        return removed;
    }

    /**
     * Removes the first history entry whose key equals the given key.
     * No-op when no key extractor is set or when the key is not present. O(n) when key is present.
     *
     * @param key The key to remove (e.g. track identifier)
     * @return true if an entry was removed, false otherwise
     */
    public boolean removeByKey(Object key) {
        if (keySet == null || !keySet.contains(key)) {
            return false;
        }
        for (int i = 0; i < history.size(); i++) {
            if (Objects.equals(key, keyExtractor.apply(history.get(i)))) {
                history.removeAt(i);
                keySet.remove(key);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the first item that matches the given predicate.
     * Used to remove a track from history when it starts playing (de-dup).
     *
     * @param predicate The condition to match (e.g. same track identifier/URI)
     * @return true if an item was removed, false otherwise
     */
    public boolean removeFirstMatching(Predicate<T> predicate) {
        if (predicate == null) {
            return false;
        }
        Iterator<T> iterator = history.iterator();
        while (iterator.hasNext()) {
            if (predicate.test(iterator.next())) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }
}
