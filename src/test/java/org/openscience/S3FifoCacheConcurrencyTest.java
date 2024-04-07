package org.openscience;

import org.junit.jupiter.api.Test;
import org.openscience.cache.Cache;
import org.openscience.cache.s3fifo.S3FifoCache;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class S3FifoCacheConcurrencyTest {
    @Test
    void shouldBeVisibleToOtherThreads() {
        Cache<Integer, String> cache = S3FifoCache.builder().setMaxCacheSize(100).build();
        ExecutorService writers = Executors.newFixedThreadPool(20);
        ExecutorService readers = Executors.newFixedThreadPool(20);

        AtomicInteger counter = new AtomicInteger();
        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(200);

        for (int i = 0; i < 200; i++) {
            writers.submit(() -> {
                int c = counter.getAndIncrement();
                queue.add(c);
                cache.put(c, String.valueOf(c));
                assertEquals(String.valueOf(c), cache.get(c)); //touch
            });
        }
        for (int i = 0; i < 200; i++) {
            readers.submit(() -> {
                Integer c = queue.poll();
                assertEquals(String.valueOf(c), cache.get(c));
            });
        }
    }
}
