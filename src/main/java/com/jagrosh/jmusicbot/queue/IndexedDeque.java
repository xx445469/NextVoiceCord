package com.jagrosh.jmusicbot.queue;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A deque backed by a circular buffer that supports O(1) access by index
 * in addition to O(1) amortized add/remove at both ends.
 * <p>
 * Index 0 is the "first" (head) element, index size()-1 is the "last" (tail).
 * Capacity grows by a factor of 2 when full; the backing array is never shrunk
 * by this class.
 *
 * @param <T> element type
 */
public class IndexedDeque<T> extends AbstractCollection<T> {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private Object[] buffer;
    private int head;  // index of first element
    private int size;

    public IndexedDeque() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public IndexedDeque(int initialCapacity) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException("initialCapacity must be positive, got: " + initialCapacity);
        }
        this.buffer = new Object[initialCapacity];
        this.head = 0;
        this.size = 0;
    }

    /**
     * Returns the element at the given index. Index 0 is the first (head) element.
     *
     * @param index index in range [0, size())
     * @return the element at that index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    @SuppressWarnings("unchecked")
    public T get(int index) {
        checkIndex(index);
        return (T) buffer[physicalIndex(index)];
    }

    /**
     * Inserts the element at the front of the deque.
     */
    public void addFirst(T e) {
        Objects.requireNonNull(e, "element");
        ensureCapacity(size + 1);
        head = decrement(head);
        buffer[head] = e;
        size++;
    }

    /**
     * Appends the element at the end of the deque.
     */
    public void addLast(T e) {
        Objects.requireNonNull(e, "element");
        ensureCapacity(size + 1);
        buffer[physicalIndex(size)] = e;
        size++;
    }

    /**
     * Removes and returns the first element.
     *
     * @throws NoSuchElementException if the deque is empty
     */
    @SuppressWarnings("unchecked")
    public T removeFirst() {
        if (size == 0) {
            throw new NoSuchElementException("deque is empty");
        }
        T e = (T) buffer[head];
        buffer[head] = null;
        head = increment(head);
        size--;
        return e;
    }

    /**
     * Removes and returns the first element, or null if the deque is empty.
     */
    public T pollFirst() {
        return size == 0 ? null : removeFirst();
    }

    /**
     * Removes and returns the last element.
     *
     * @throws NoSuchElementException if the deque is empty
     */
    @SuppressWarnings("unchecked")
    public T removeLast() {
        if (size == 0) {
            throw new NoSuchElementException("deque is empty");
        }
        int last = physicalIndex(size - 1);
        T e = (T) buffer[last];
        buffer[last] = null;
        size--;
        return e;
    }

    /**
     * Removes and returns the last element, or null if the deque is empty.
     */
    public T pollLast() {
        return size == 0 ? null : removeLast();
    }

    /**
     * Returns the first element without removing it.
     */
    @SuppressWarnings("unchecked")
    public T peekFirst() {
        if (size == 0) {
            return null;
        }
        return (T) buffer[head];
    }

    /**
     * Returns the last element without removing it.
     */
    @SuppressWarnings("unchecked")
    public T peekLast() {
        if (size == 0) {
            return null;
        }
        return (T) buffer[physicalIndex(size - 1)];
    }

    /**
     * Removes and returns the element at the given index.
     * Shifts the smaller segment to minimize copy count (O(min(i, n-i))).
     *
     * @param index index in range [0, size())
     * @return the removed element
     * @throws IndexOutOfBoundsException if index is out of range
     */
    @SuppressWarnings("unchecked")
    public T removeAt(int index) {
        checkIndex(index);
        int physical = physicalIndex(index);
        T removed = (T) buffer[physical];

        if (index < size / 2) {
            // Shift [0..index-1] one slot toward the tail (right in logical order)
            for (int i = index; i > 0; i--) {
                int to = physicalIndex(i);
                int from = physicalIndex(i - 1);
                buffer[to] = buffer[from];
            }
            buffer[head] = null;
            head = increment(head);
        } else {
            // Shift [index+1..size-1] one slot toward the head (left in logical order)
            for (int i = index; i < size - 1; i++) {
                int to = physicalIndex(i);
                int from = physicalIndex(i + 1);
                buffer[to] = buffer[from];
            }
            buffer[physicalIndex(size - 1)] = null;
        }
        size--;
        return removed;
    }

    /**
     * Adds the element at the front (same as addFirst).
     * Required by AbstractCollection for add() at the "beginning" of the collection view.
     */
    @Override
    public boolean add(T e) {
        addFirst(e);
        return true;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        if (head + size <= buffer.length) {
            Arrays.fill(buffer, head, head + size, null);
        } else {
            Arrays.fill(buffer, head, buffer.length, null);
            Arrays.fill(buffer, 0, head + size - buffer.length, null);
        }
        size = 0;
    }

    @Override
    public Iterator<T> iterator() {
        return new IndexedDequeIterator();
    }

    private final class IndexedDequeIterator implements Iterator<T> {
        private int cursor;
        private int lastReturned = -1;

        IndexedDequeIterator() {
            this.cursor = 0;
        }

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            lastReturned = cursor;
            return (T) buffer[physicalIndex(cursor++)];
        }

        @Override
        public void remove() {
            if (lastReturned < 0) {
                throw new IllegalStateException("no element to remove");
            }
            IndexedDeque.this.removeAt(lastReturned);
            cursor = lastReturned;
            lastReturned = -1;
        }
    }

    private int physicalIndex(int logicalIndex) {
        return (head + logicalIndex) % buffer.length;
    }

    private int increment(int i) {
        return (i + 1) % buffer.length;
    }

    private int decrement(int i) {
        return (i - 1 + buffer.length) % buffer.length;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= buffer.length) {
            return;
        }
        int newCapacity = Math.max(buffer.length * 2, minCapacity);
        Object[] newBuffer = new Object[newCapacity];
        for (int i = 0; i < size; i++) {
            newBuffer[i] = buffer[physicalIndex(i)];
        }
        buffer = newBuffer;
        head = 0;
    }
}
