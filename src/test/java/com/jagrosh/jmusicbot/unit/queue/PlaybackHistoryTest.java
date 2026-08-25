package com.jagrosh.jmusicbot.unit.queue;

import com.jagrosh.jmusicbot.queue.PlaybackHistory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class PlaybackHistoryTest {

    @Test
    public void testAddAndSize() {
        PlaybackHistory<String> history = new PlaybackHistory<>(3);
        
        history.add("one");
        assertEquals(1, history.size());
        assertEquals("one", history.get(0));

        history.add("two");
        assertEquals(2, history.size());
        assertEquals("two", history.get(0));
        assertEquals("one", history.get(1));
    }

    @Test
    public void testMaxSize() {
        PlaybackHistory<String> history = new PlaybackHistory<>(2);

        history.add("one");
        history.add("two");
        history.add("three");

        assertEquals(2, history.size());
        assertEquals("three", history.get(0));
        assertEquals("two", history.get(1));
        
        // Ensure "one" was removed (it was the oldest)
        List<String> list = history.getList();
        assertFalse(list.contains("one"));
    }

    @Test
    public void testRemoveFirst() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        history.add("one");
        history.add("two");

        assertEquals("two", history.removeFirst());
        assertEquals(1, history.size());
        assertEquals("one", history.get(0));

        assertEquals("one", history.removeFirst());
        assertTrue(history.isEmpty());
        assertNull(history.removeFirst());
    }

    @Test
    public void testSetMaxSizeShrink() {
        PlaybackHistory<String> history = new PlaybackHistory<>(5);
        history.add("1");
        history.add("2");
        history.add("3");
        history.add("4");
        history.add("5");

        history.setMaxSize(2);
        assertEquals(2, history.size());
        assertEquals("5", history.get(0));
        assertEquals("4", history.get(1));
    }

    @Test
    public void testSetNegativeMaxSize() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        assertThrows(IllegalArgumentException.class, () -> history.setMaxSize(-1));
    }

    @Test
    public void testSetZeroMaxSize() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        history.add("one");
        history.add("two");
        history.setMaxSize(0);

        assertEquals(0, history.getMaxSize());
        assertTrue(history.isEmpty());
        assertEquals(0, history.size());
    }

    @Test
    public void testClear() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        history.add("one");
        history.clear();
        assertTrue(history.isEmpty());
        assertEquals(0, history.size());
    }

    @Test
    public void testAddNullThrows() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        assertThrows(NullPointerException.class, () -> history.add(null));
    }

    @Test
    public void testConstructorRejectsNegativeSize() {
        PlaybackHistory<String> history = new PlaybackHistory<>(0);
        assertEquals(0, history.getMaxSize());
        assertTrue(history.isEmpty());

        assertThrows(IllegalArgumentException.class, () -> new PlaybackHistory<String>(-1));
    }

    @Test
    public void testAddIsNoopWhenHistoryDisabled() {
        PlaybackHistory<String> history = new PlaybackHistory<>(0);
        history.add("a");
        history.add("b");

        assertEquals(0, history.getMaxSize());
        assertEquals(0, history.size());
        assertTrue(history.getList().isEmpty());
    }

    @Test
    public void testTransitionFromDisabledToEnabledCollectsEntries() {
        PlaybackHistory<String> history = new PlaybackHistory<>(0);
        history.add("ignored");
        history.setMaxSize(2);
        history.add("a");
        history.add("b");

        assertEquals(2, history.getMaxSize());
        assertEquals(2, history.size());
        assertEquals("b", history.get(0));
        assertEquals("a", history.get(1));
    }

    @Test
    public void testTransitionPositiveToDisabledClearsAndDedupStateResets() {
        PlaybackHistory<String> history = new PlaybackHistory<>(3, s -> s);
        history.add("a");
        history.add("b");
        history.setMaxSize(0);
        history.add("c");

        assertEquals(0, history.getMaxSize());
        assertTrue(history.isEmpty());

        history.setMaxSize(2);
        history.add("x");
        history.add("x");
        assertEquals(1, history.size());
        assertEquals("x", history.get(0));
    }

    @Test
    public void testRemoveAt() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        history.add("a");
        history.add("b");
        history.add("c");

        assertEquals("c", history.removeAt(0));
        assertEquals(2, history.size());
        assertEquals("b", history.get(0));

        assertEquals("a", history.removeAt(1));
        assertEquals(1, history.size());
        assertEquals("b", history.get(0));

        assertNull(history.removeAt(5));
        assertNull(history.removeAt(-1));
    }

    @Test
    public void testRemoveFirstMatching() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        history.add("x");
        history.add("y");
        history.add("z");

        assertTrue(history.removeFirstMatching("y"::equals));
        assertEquals(2, history.size());
        assertEquals("z", history.get(0));
        assertEquals("x", history.get(1));

        assertFalse(history.removeFirstMatching("y"::equals));
        assertTrue(history.removeFirstMatching("z"::equals));
        assertEquals(1, history.size());
        assertEquals("x", history.get(0));

        assertFalse(history.removeFirstMatching(null));
    }

    @Test
    public void testKeyExtractorDuplicateKeyOnlyOneEntry() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10, s -> s);
        history.add("a");
        history.add("a");
        assertEquals(1, history.size());
        assertEquals("a", history.get(0));
    }

    @Test
    public void testKeyExtractorSameKeyMovesToFront() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10, s -> s);
        history.add("a");
        history.add("b");
        history.add("a");
        assertEquals(2, history.size());
        assertEquals("a", history.get(0));
        assertEquals("b", history.get(1));
    }

    @Test
    public void testKeyExtractorNullPreservesExistingBehavior() {
        PlaybackHistory<String> history = new PlaybackHistory<>(10);
        history.add("a");
        history.add("a");
        assertEquals(2, history.size());
        assertEquals("a", history.get(0));
        assertEquals("a", history.get(1));
    }
}
