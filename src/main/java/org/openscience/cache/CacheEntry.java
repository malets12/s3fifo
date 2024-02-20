package org.openscience.cache;

import java.util.concurrent.atomic.AtomicInteger;

public interface CacheEntry<K, V> {
    K key();

    V value();

    /** Frequency of access of this entry.
     * @return frequency counter
     */
    AtomicInteger freq();
}
