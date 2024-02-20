package org.openscience.cache.s3fifo;

import org.openscience.cache.CacheEntry;

import java.util.concurrent.atomic.AtomicInteger;

public record S3FifoCacheEntry<K, V> (K key, V value, AtomicInteger freq) implements CacheEntry<K, V> {

    public S3FifoCacheEntry(K key, V value) {
        this(key, value, new AtomicInteger());
    }
}
