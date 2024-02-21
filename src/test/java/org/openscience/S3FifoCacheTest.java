package org.openscience;

import org.junit.jupiter.api.Test;
import org.openscience.cache.Cache;
import org.openscience.cache.s3fifo.S3FifoCache;

import static org.junit.jupiter.api.Assertions.*;

class S3FifoCacheTest {

    @Test
    void shouldPushAndRead() {
        Cache<String, String> cache = new S3FifoCache<>(10);

        cache.put("apple", "red");
        cache.put("banana", "yellow");
        assertEquals("red", cache.get("apple"));
        assertEquals("yellow", cache.get("banana"));
        assertEquals("yellow", cache.get("banana"));
        assertEquals("yellow", cache.get("banana"));

        cache.put("apple", "green");
        assertEquals("green", cache.get("apple"));
    }

    @Test
    void shouldEvictScale() {
        Cache<String, String> cache = new S3FifoCache<>(100);

        for (int i = 0; i < 100; i++) {
            cache.put("key" + i, "value" + i);
        }

        for (int i = 90; i < 100; i++) {
            assertEquals("value" + i, cache.get("key" + i));
        }

        for (int i = 100; i < 1000; i++) {
            cache.put("key" + i, "value" + i);
        }

        for (int i = 500; i < 600; i++) {
            assertNull(cache.get("key" + i));
        }
    }

    @Test
    void shouldProtectTouched() {
        Cache<String, String> cache = new S3FifoCache<>(100);

        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, "value" + i);
            assertEquals("value" + i, cache.get("key" + i)); //touch
        }

        for (int i = 20; i < 40; i++) {
            cache.put("key" + i, "value" + i);
        }

        for (int i = 0; i < 10; i++) {
            assertEquals("value" + i, cache.get("key" + i));
        }

        for (int i = 30; i < 40; i++) {
            assertEquals("value" + i, cache.get("key" + i));
        }
    }

    @Test
    void shouldEvictPart() {
        Cache<String, String> cache = new S3FifoCache<>(100);

        for (int i = 0; i < 200; i++) {
            cache.put("key" + i, "value" + i);
            cache.get("key" + i); //touch
        }

        for (int i = 0; i < 200; i++) {
            cache.get("key" + i); //touch
        }

        for (int i = 0; i < 200; i++) {
            cache.put("key" + i, "value" + i);
        }

        for (int i = 110; i < 200; i++) {
            assertEquals("value" + i, cache.get("key" + i));
        }
    }

    @Test
    void shouldMoveToGhost() {
        Cache<String, String> cache = new S3FifoCache<>(100);

        for (int i = 0; i < 100; i++) {
            cache.put("key" + i, "value" + i);
            assertEquals("value" + i, cache.get("key" + i)); //touch
        }

        for (int i = 0; i < 110; i++) {
            cache.put("key" + i, "value" + i);
        }

        assertNull(cache.get("key0"));

        for (int i = 100; i < 300; i++) {
            cache.put("key" + i, "value" + i);
        }

        assertNull(cache.get("key190"));
    }

    @Test
    void shouldGetFromGhostAndReduceGhostSize() {
        Cache<String, String> cache = new S3FifoCache<>(100);

        for (int i = 0; i < 100; i++) {
            cache.put("key" + i, "value" + i);
            cache.get("key" + i);
            cache.get("key" + i);
        }

        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, "value" + i);
        }

        for (int i = 100; i < 200; i++) {
            cache.put("key" + i, "value" + i);
        }

        for (int i = 0; i < 100; i++) {
            cache.get("key" + i);
        }

        for (int i = 100; i < 110; i++) {
            cache.put("key" + i, "value" + i);
        }

        for (int i = 300; i < 310; i++) {
            cache.put("key" + i, "value" + i);
        }

        assertTrue(true);
        assertNull(cache.get("key110"));
        assertNull(cache.get("key17"));
    }
}