package org.openscience.cache.s3fifo;

import org.openscience.cache.Cache;
import org.openscience.cache.CacheEntry;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Blog <a href="https://blog.jasony.me/system/cache/2023/08/01/s3fifo">post</a> about
 * Simple, Scalable eviction algorithm with three Static FIFO queues (S3-FIFO)
 */
public final class S3FifoCache<K, V> implements Cache<K, V> {
    private static final int MAX_FREQUENCY_LIMIT = 3;
    private final int maxMainSize;
    private final int maxGhostSize;
    private final int maxSmallSize;
    /**
     * Small queue for entries with low frequency.
     */
    private final Deque<CacheEntry<K, V>> smallQ;
    /**
     * Main queue for entries with high frequency.
     */
    private final Deque<CacheEntry<K, V>> mainQ;
    /**
     * Ghost queue for evicted entry keys.
     * Guarantees ordering.
     * Could be outdated
     */
    private final Deque<K> ghostQ;
    /**
     * Set of evicted entry keys.
     * Guarantees O(log(n)) access.
     * Primary source of truth about state of ghost.
     */
    private final Set<K> ghost;
    /**
     * Map of all entries from smallQ and mainQ.
     */
    private final Map<K, CacheEntry<K, V>> entries;

    /**
     * Creates a new cache with the given maximum size.
     *
     * @param maxCacheSize maximum cache size (smallQ + mainQ, same as entries)
     * @param maxGhostSize maximum ghostQ size (same as ghost)
     * @throws IllegalArgumentException if the maximum capacity of
     * {@code maxCacheSize} or {@code maxGhostSize} is negative or zero
     */
    private S3FifoCache(int maxCacheSize, int maxGhostSize) {
        this.maxSmallSize = maxCacheSize / 10;
        this.maxMainSize = maxCacheSize - maxSmallSize;
        this.maxGhostSize = maxGhostSize;
        this.smallQ = new ConcurrentLinkedDeque<>();
        this.mainQ = new ConcurrentLinkedDeque<>();
        this.ghostQ = new ConcurrentLinkedDeque<>();
        this.ghost = ConcurrentHashMap.newKeySet((int) (maxGhostSize / 0.75) + 8);
        this.entries = new ConcurrentHashMap<>((int) (maxCacheSize / 0.75) + 8);
    }

    /**
     * Returns the value to which the specified {@code key} is mapped,
     * or null if cache contains no mapping for the {@code key}.
     *
     * @param key key with which the specified value is to be associated
     * @throws NullPointerException if the specified {@code key} is null
     * @return null or value to which the specified {@code key} is mapped
     */
    @Override
    public V get(K key) {
        Objects.requireNonNull(key);
        CacheEntry<K, V> entry = this.entries.get(key);
        if (entry != null) {
            entry.freq().accumulateAndGet(1, (oldVal, inc) -> Math.min(oldVal + inc, MAX_FREQUENCY_LIMIT));
            return entry.value();
        } else {
            return null;
        }
    }

    /**
     * Inserts a new entry with the given {@code key} and {@code value} into the cache.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @throws NullPointerException if the specified key or value is null
     */
    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        evict();

        CacheEntry<K, V> entry = new S3FifoCacheEntry<>(key, value);
        if (this.ghost.contains(key)) {
            //Move to mainQ
            this.mainQ.offerFirst(entry);
            this.ghost.remove(key);
        } else if (!this.entries.containsKey(key)) { //Avoid duplicates
            //New entry
            this.smallQ.offerFirst(entry);
        }
        this.entries.put(key, entry);
    }

    private void evict() {
        if (entries.size() >= this.maxMainSize + this.maxSmallSize) {
            if (this.smallQ.size() >= this.maxSmallSize) {
                evictSmall();
            } else {
                evictMain();
            }
        }
    }

    private void evictMain() {
        CacheEntry<K, V> tail;
        while ((tail = this.mainQ.pollLast()) != null) {
            AtomicInteger freq = tail.freq();
            if (freq.getAcquire() > 0) {
                this.mainQ.offerFirst(tail);
                freq.decrementAndGet();
            } else {
                this.entries.remove(tail.key());
                break;
            }
        }
    }

    private void evictSmall() {
        CacheEntry<K, V> tail;
        while ((tail = this.smallQ.pollLast()) != null) {
            AtomicInteger freq = tail.freq();
            if (freq.getAcquire() > 0) {
                freq.setRelease(0);
                this.mainQ.offerFirst(tail);
                if (this.mainQ.size() >= this.maxMainSize) {
                    evictMain();
                }
            } else {
                this.entries.remove(tail.key());
                insertGhost(tail);
                break;
            }
        }
    }

    private void insertGhost(CacheEntry<K, V> tail) {
        int ghostDelta = this.ghostQ.size() - this.maxGhostSize;
        if (this.ghost.size() >= this.maxGhostSize || ghostDelta >= 0) {
            //Clean ghostQ and try to sync with ghost
            K key;
            while ((key = this.ghostQ.pollLast()) != null && ghostDelta >= 0) {
                if (this.ghost.contains(key)) {
                    this.ghost.remove(key);
                    break;
                }
                ghostDelta--;
            }
        }
        this.ghostQ.offerFirst(tail.key());
        this.ghost.add(tail.key());
    }

    public static Builder<Object, Object> builder() {
        return new Builder<>();
    }

    public static final class Builder<K, V> {
        private int maxCacheSize;
        private int maxGhostSize;

        private Builder() {}

        public Builder<K, V> setMaxCacheSize(int maxCacheSize) {
            throwIllegalArgumentExceptionWhen(maxCacheSize <= 0);
            this.maxCacheSize = maxCacheSize;
            return this;
        }

        public Builder<K, V> setMaxGhostSize(int maxGhostSize) {
            throwIllegalArgumentExceptionWhen(maxGhostSize <= 0);
            this.maxGhostSize = maxGhostSize;
            return this;
        }

        public <K1 extends K, V1 extends V> Cache<K1, V1> build() {
            throwIllegalArgumentExceptionWhen(this.maxCacheSize == 0);
            if (this.maxGhostSize == 0) {
                this.maxGhostSize = this.maxCacheSize - this.maxCacheSize / 10;
            }
            return new S3FifoCache<>(this.maxCacheSize, this.maxGhostSize);
        }

        private void throwIllegalArgumentExceptionWhen(boolean condition) {
            if (condition) {
                throw new IllegalArgumentException();
            }
        }
    }
}
