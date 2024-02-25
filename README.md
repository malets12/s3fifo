# S3 FIFO cache
Java thread-safe implementation of Simple, Scalable eviction algorithm with three Static FIFO queues (S3-FIFO).
More information [here](https://blog.jasony.me/system/cache/2023/08/01/s3fifo).

## Basic usage

````java
import org.openscience.cache.Cache;
import org.openscience.cache.s3fifo.S3FifoCache;
//...

int size = 100;
Cache<String, String> cache = new S3FifoCache<>(size);
//or use extended constructor if you want to define ghost size manually

cache.set("key1", "value1");
String value = cache.get("key1"); // value1

````

## Compatibility
Please use Java 17 LTS or higher.
