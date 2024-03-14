# Walkthrough
The one billion row challenge (1brc) is a fun exploration of how fast a Java application can parse and aggregate a file containing 1 billion rows.
In this article you can learn the optimizations I applied at the challenge plus some easy ones you can apply in everyday coding.

 ### Challenge
You are given a file with 1 billion rows. Each row contains a text station and a numeric temperature, separated by a comma: 

```text
Hamburg;12.0
Bulawayo;8.9
Palembang;38.8
Istanbul;15.2
Cracow;12.6
...
(1 billion rows in total)
```

The data has the following constraints:
 * Station - a UTF-8 string from 1 to 100 bytes. No '.' and '\u0000' characters allowed.
 * Temperature - a numeric value in the format: #.#, ##.#, -#.#, -##.#. Exactly one fractional digit.
 * Unique stations <= 10k - the total number of unique stations is less or equal to 10000.

The task is to write a Java program to find `min/max/avg` of temperature per station. The result is sorted alphabetically by station:
```
{Abha=-23.0/18.0/59.2, Abidjan=-16.2/26.0/67.3, Abéché=-10.0/29.4/69.0, Accra=-10.1/26.4/66.4, Addis Ababa=-23.7/16.0/67.0, Adelaide=-27.8/17.3/58.5, ...}
```

You have a whole month to complete this simple task. A whole month? Yep, sounds easy. But yet there is one condition - the program must be as fast as possible.

### Data
Well, the constraints are just constraints, but let's look at the real data. 
Hopefully for us, there is a [generator](https://github.com/gunnarmorling/1brc/blob/main/src/main/java/dev/morling/onebrc/CreateMeasurements.java) in the [codebase](https://github.com/gunnarmorling/1brc/) to generate a file.
Looking into the source code, we see that it uses only 413 unique stations. Let's collect some stats on station lengths. The main dataset:
* 0-7   - 52.0 %
* 8-15  - 45.5 %
* 16-26 - 2.5 %

The station names are really short in most of the cases. Let's keep it in mind.

There is one more [generator](https://github.com/gunnarmorling/1brc/blob/main/src/main/java/dev/morling/onebrc/CreateMeasurements3.java) to generate a file with 10k unique stations. 
Let's analyze it as well, it is always interesting to see how different optimizations can target different data. The bonus dataset:
* 0-7    - 76.41 %
* 8-15   -  9.84 %
* 16-57  - 11.31 %
* 57-100 -  5.34 %

Here is 16.65% of data with long station names from 16 to 100 inclusively.

### Evaluation
Unfortunately, I was not able to find the same instance which was used at the official evaluation.
But that is not a big deal. How many of you target specific hardware with your Java program? 
Not many, so let's spin c7a.4xlarge instance in AWS and test the numbers there. 
Instance spec:
* CPU: AMD EPYC 9R14 16 CPUs
* MEM: DDR5 4800 MT/s 32 GB
* AFFINITY: taskset 0-7

We will be using `hyperfine --warmup 3 --runs 10` to measure the execution time. So let's begin.

### 00 - Baseline
We begin wil a simple and short solution similar to the original one provided by the author of the challenge:
```java
void solve(String[] args, Path file, PrintStream output) {
    Map<String, Aggregate> result = Files.lines(file)            // read line by line
        .map((line) -> {
            String[] parts = line.split(";");                    // split line into array[2]
            String station = parts[0];                          
            double temperature = Double.parseDouble(parts[1]);
            return new Measurement(station, temperature);
         })
        .collect(groupingBy(Measurement::station, Collector.of(  // group by station
            Aggregate::new, 
            (aggregate, measurement) -> {                        // aggregation function
                aggregate.min = Math.min(aggregate.min, measurement.temperature);
                aggregate.max = Math.max(aggregate.max, measurement.temperature);
                aggregate.sum += measurement.temperature;
                aggregate.cnt++;
            },
            (aggregate, aggregate2) -> {
                throw new IllegalStateException("Not called with 1 thread");
            }
    )));

    output.println(new TreeMap<>(result)); // sort and print
}
```
It is simple and nice. Let's test execution time:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 

Good, it's like x2 faster on this instance than the original result. Not a problem, we will compare the numbers with this baseline.

### 01 - Substring
Let's do a quick warmup session. The code above uses `String::split`. Reading javadoc, it should be using a regex.
But we do not trust javadoc, we trust code. Going deeper, we see that there is a fast path for ';' separator:

```java
String[] split(String regex, int limit, boolean withDelimiters) {
    char ch = 0;
    if (regex.length() == 1 && ".$|()[{^?*+\\".indexOf(ch = regex.charAt(0)) == -1) {
        return split(ch, limit, withDelimiters);
    }
    // slow path with Pattern
}
```

Going deeper, we see that the fast path uses `String::substring` and creates a list then an array.
All this work seems to be expensive, we know our data too well. We have only two parts in a row: `station;temperature`. 
So just using `String::substring` will be sufficient. 

```java
void solve(String[] args, Path file, PrintStream output) {
    Map<String, Aggregate> result = Files.lines(file)
        .map((line) -> {                    // called 1M times
            int comma = line.indexOf(';');  
            String station = line.substring(0, comma);
            double temperature = Double.parseDouble(line.substring(comma + 1));
            return new Measurement(station, temperature);
        })
        // .collect ...   
}
```

Let's test it:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 01 | Substring          | 104.383 ± 0.363 |          -16.93 | 132.624 ± 1.320 |          -17.17 |

Not bad for this small change. So picking a better-fit method is always a good idea. The warmup is finished.

### 02 - No Garbage
Our lambda is called 1M times to parse a row. It creates a `Measurement` object and several `String` objects. 
But at the end we only have 413 entries. Let's see how much time we spend doing GC:

```text
[0.002s][info][gc] Using Parallel
[0.779s][info][gc] GC(0) Pause Young (Allocation Failure) 1025M->1M(3925M) 1.242ms
[1.286s][info][gc] GC(1) Pause Young (Allocation Failure) 1025M->1M(3925M) 0.466ms
[1.774s][info][gc] GC(2) Pause Young (Allocation Failure) 1025M->1M(3925M) 0.380ms
...
[102.727s][info][gc] GC(159) Pause Young (Allocation Failure) 1365M->1M(4095M) 0.236ms
[103.372s][info][gc] GC(160) Pause Young (Allocation Failure) 1365M->1M(4095M) 0.171ms

GC stats: time=34.711 sec, count=161
```

Well, 34.711 seconds is way too much. Let's rewrite our code to be allocation-free when parsing a row.
This loop reads a file chunk by chunk:

```java
void solve(String[] args, Path file, PrintStream output) {
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
        ByteBuffer buffer = ByteBuffer.allocate(2 * 1024 * 1024);  // 2 MB, heap buffer
        Map<Key, Aggregate> aggregates = new HashMap<>();
        Key key = new Key();  // mutable key
    
        while (channel.read(buffer) > 0) {
            int limit = buffer.hasRemaining()
                ? buffer.position()        // ends, no partial rows
                : buffer.capacity() - 128; // leaves some space to skip a partial row
        
            buffer.flip();
            loop(buffer, limit, key, aggregates);
            buffer.compact();
        }
        // ... sort and print
    }
}
```

And the main loop looks like this:
```java
class Key {
    final byte[] array;
    int length;
    // copy constructor, equals, hashcode
} 

void loop(ByteBuffer buffer, int limit, Key key, Map<Key, Aggregate> aggregates) {
    while (buffer.position() < limit) {  // called 1B times
        {  // parse station
            key.length = 0;
            for (byte b; (b = buffer.get()) != ';'; ) {
                key.array[key.length++] = b;
            }
        }
       
        double temperature = 0.0;
        {  // parse temperature: -##.#, -#.#, #.#, ##.#
            int sign = 1;
       
            for (byte b; (b = buffer.get()) != '\n'; ) {
                if (b == '-') {
                    sign = -1;
                } else if (b != '.') {
                    temperature = 10 * temperature + (b - '0');
                }
            }
          
            temperature = sign * temperature / 10; // exactly one fractional digit
        }
       
        Aggregate aggregate = aggregates.get(key);
       
        if (aggregate == null) {  // miss, create key and aggregate, called 413 times
            Key copy = new Key(key);
            aggregate = new Aggregate();
            aggregates.put(copy, aggregate);
        }
       
        aggregate.min = Math.min(aggregate.min, temperature);
        aggregate.max = Math.max(aggregate.max, temperature);
        aggregate.sum += temperature;
        aggregate.cnt++;
    }
}
```
The idea is to reuse a `Key` object to store station data to look up in a map and only allocate objects when meeting a new key.
It happens only 413 times. So our code becomes allocation-free on the hot path.
Let's prove it:

```text
[0.002s][info][gc] Using Parallel
GC stats: time=0 sec, count=0
```

Let's test it:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 01 | Substring          | 104.383 ± 0.363 |          -16.93 | 132.624 ± 1.320 |          -17.17 |
| 02 | NoGarbage          |  41.485 ± 0.139 |          -60.26 |  61.337 ± 0.152 |          -53.75 |

Nice, much better!

### 02 - Direct Buffer
Our code allocates heap buffer to read chunks of a file:

```java
ByteBuffer buffer = ByteBuffer.allocate(2 * 1024 * 1024);  // heap buffer
```

Going deeper, you will find out that `FileChannel::read` picks a `DirectBuffer` from a pool to call a system function
to read a chunk of a file, then copies the data from the `DirectBuffer` to your heap buffer.
So let's just create a `DirectBuffer` to eliminate one copy.

```java
ByteBuffer buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024);  // off-heap buffer
```

Let's test:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 02 | NoGarbage          |  41.485 ± 0.139 |          -60.26 |  61.337 ± 0.152 |          -53.75 |
| 03 | DirectBuffer       |  39.792 ± 0.264 |           -4.08 |  56.018 ± 0.183 |           -8.67 |

Good enough for such a simple change.

### 03 - Mapped Segment
OS has a page cache to cache file content (pages). Our file is around 13 GB in size, it fits completely in memory on this instance.
When we call `FileChanel::read` it copies the data from the cache in our off-heap buffer. 
So we can map the file into the process's address space instead, thus eliminating one more copy:

```java
FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), Arena.global());

byte b1 = buffer.get();                                   // replace ByteBuffer::get
byte b2 = segment.get(ValueLayout.JAVA_BYTE, position++); // with MemorySegment::get
```

MemorySegment is new API for working with native memory. It helps us to map a file with size greater than 2 GB.
Rewriting our loop to work with MemorySegment. Let's test it:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 03 | DirectBuffer       |  39.792 ± 0.264 |           -4.08 |  56.018 ± 0.183 |           -8.67 |
| 04 | MappedSegment      |  40.173 ± 0.203 |            0.96 |  57.840 ± 0.297 |            3.25 |

WOW! It does not seem to help. The performance drop is reproducible on different machines.
The new API might be not yet that optimized as ByteBuffer.
But in general it is good technique, especially when a file "sits" in the page cache.
Anyway we will keep this change.

### 04 - Parallelism
It is time to grab all our cpus. Let's keep it simple by dividing our file into N even segments. N is 8 for our evaluation.
```java
Aggregator[] split(MemorySegment segment) { 
    int parallelism = Runtime.getRuntime().availableProcessors();  // = 8
    long size = segment.byteSize();                       // ~ 13GB                                            
    long chunk = (size + parallelism - 1) / parallelism;  // ~ 13GB / 8 = 1.625 GB
    Aggregator[] aggregators = new Aggregator[parallelism];
    
    long position = 0;
    for (int i = 0; i < parallelism; i++) {
        long limit = nextLine(segment, Math.min(position + chunk, size)); // find next line
        aggregators[i] = new Aggregator(segment, position, limit);        // create 8 threads
        position = limit;
    }
    
    return aggregators;
}
```
Then run the threads, wait when they finish and merge all results into one map.
```java
void solve(String[] args, Path file, PrintStream output) {
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
        MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), Arena.global());
        Aggregator[] aggregators = split(segment);
        
        for (Aggregator aggregator : aggregators) {
            aggregator.start();                          // run threads
        }
        
        Map<String, Aggregate> result = new TreeMap<>(); // for sorting and output
        
        for (Aggregator aggregator : aggregators) {
            aggregator.join();                          // wait for thread to finish
            merge(aggregator.aggregates, result);       // merge result into final map 
        }
     
        output.println(result);
    }
}
```

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 04 | MappedSegment      |  40.173 ± 0.203 |            0.96 |  57.840 ± 0.297 |            3.25 |
| 05 | Parallelism        |   5.583 ± 0.378 |          -86.10 |   8.018 ± 0.385 |          -86.14 |

As expected, we got almost x8 improvement simply by utilizing 8 cpus.

### 06 - Hash While Parsing
It is time to optimize our loop. `Key::hashCode` loops over station to compute hash:

```java
class Key {
    final byte[] array;
    int length;

    int hashCode() {
        int hash = 0;
        for (int i = 0; i < length; i++) {
            hash = 71 * hash + array[i];
        }
        return hash;
    }
}    
```

Why? Let's compute hash while parsing! It will eliminate one array read:

```java
void loop(MemorySegment segment, long position, long limit, Key key, Map<Key, Aggregate> aggregates) {
    while (position < limit) {
        {  // 1-byte parsing for stations
            key.length = 0;
            key.hash = 0;
            for (byte b; (b = segment.get(ValueLayout.JAVA_BYTE, position++)) != ';'; ) {
                key.array[key.length++] = b;
                key.hash = 71 * key.hash + b;  // eliminates array read when inserting in map
            }
        }
        // temperature, lookup, update...
    }
}        
```

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 05 | Parallelism        |   5.583 ± 0.378 |          -86.10 |   8.018 ± 0.385 |          -86.14 |
| 06 | HashWhileParsing   |   5.212 ± 0.466 |           -6.64 |   7.649 ± 0.933 |           -4.59 |

Nice!

### 07 - Simple Map
It is time to write our own hash map, which can change and tune later.
```java
class Aggregates { // keys <= 10K, no need to grow
    final Key[] keys = new Key[64 * 1024];
    final Aggregate[] values = new Aggregate[keys.length];

    Aggregate put(Key key) {
        // hash % mask == hash & mask, because map size is a power of two 
        // so we can use this trick instead of heavy div
        for (int mask = keys.length - 1, index = mix(key.hash) & mask; ; index = (index + 1) & mask) {
            Key candidate = keys[index];

            if (candidate == null) {      // called 413 times
                Aggregate value = new Aggregate();
                keys[index] = new Key(key);  // copy key
                values[index] = value;
                return value;
            }

            if (candidate.equals(key)) {  // called ~1B times
                return values[index];
            }
        }
    }
}
```
Here is the first bit trick. To compute the index of an entry in keys array we need to do `hash % keys.length`.
But because `keys.length` is a power of two, we can do `hash & (keys.length)` instead. A division is heavier than masking.
This trick is used in maps, queues and other data structures.
Next, we see that we allocate pretty large arrays. It has pros and cons:
* No need to write resize logic. Unique keys <= 10K.
* Reduces the number of collisions.
* Increases cache misses only for pointer's arrays. Since we have only 413 entries, we still mostly target L1 cache.
* Increases TLB misses if going too big with 4 KB pages.

Let's collect collision stats:

| #  | Change      | Stations 413 | Stations 10k |
|----|-------------|-------------:|-------------:|
| 06 | SimpleMap   |            3 |        ~1053 |

So it is a trade-off between collisions and data locality.
Going bigger does not make sense. 
It is better to tune the hash function and the map for the main dataset later.
Let's test it:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 06 | HashWhileParsing   |   5.212 ± 0.466 |           -6.64 |   7.649 ± 0.933 |           -4.59 |
| 07 | SimpleMap          |   4.915 ± 0.280 |           -5.70 |   7.211 ± 0.829 |           -5.73 |

Nice!

### 08 - Branchy Temperature
What about temperature parsing. Temperature format: -#.#, -##.#, ##.#, ##.#. 
We can use branches instead of a loop to parse it. 
We can use int instead of double to store temperature * 10 and only divide it by 10 in the end, not in the loop.

```java
void loop(MemorySegment segment, long position, long limit, Key key, Aggregates aggregates) {
    while (position < limit) {
        // ... parse station
       
        int temperature;  // temperature * 10, e.g -12.3 -> -123
        {   // temperature: -##.#, -#.#, #.#, ##.#
            int sign = 1;
            byte b0 = segment.get(ValueLayout.JAVA_BYTE, position);

            if (b0 == '-') {
                sign = -1;
                b0 = segment.get(ValueLayout.JAVA_BYTE, ++position);
            }

            byte b1 = segment.get(ValueLayout.JAVA_BYTE, position + 1);

            if (b1 == '.') {    // #.#
                byte b2 = segment.get(ValueLayout.JAVA_BYTE, position + 2);
                temperature = sign * (10 * (b0 - '0') + (b2 - '0'));
                position += 4;  // + \n
            } else {            // ##.#
                byte b3 = segment.get(ValueLayout.JAVA_BYTE, position + 3);
                temperature = sign * (100 * (b0 - '0') + (10 * (b1 - '0')) + (b3 - '0'));
                position += 5;  // + \n
            }
        }
       
        // ... lookup and update
    }
}
```

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 07 | SimpleMap          |   4.915 ± 0.280 |           -5.70 |   7.211 ± 0.829 |           -5.73 |
| 08 | BranchyTemperature |   4.220 ± 0.181 |          -14.14 |   6.623 ± 0.555 |           -8.15 |

Nice!

### 09 - Unsafe
`sun.misc.Unsafe` is private API, which is being considered to be banned in the future Java releases.
It will help us to work with mapped memory without bound checks.
It is still possible to craft a decent loop using public API, which eliminates some or most of bounds checks.
But the problem is that it is much harder to do so than just using Unsafe:(. Let's replace:

```java
MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), Arena.global());
long address = segment.address();

byte safeByte = segment.get(ValueLayout.JAVA_BYTE, 0);  // replace all occurrences
byte unsafeByte =  UNSAFE.getByte(address);             // with UNSAFE.getByte(address)
```

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 |
| 08 | BranchyTemperature |   4.220 ± 0.181 |          -14.14 |   6.623 ± 0.555 |           -8.15 |
| 09 | UnsafeParsing      |   3.861 ± 0.220 |           -8.51 |   6.035 ± 0.574 |           -8.88 |

Nice! Not a big deal when performance is not a concern. But Unsafe is quite extensively used in high-performance libraries.
When people want to squeeze out every drop of performance. Let's see what happens in the future releases.

### 10 - No Key Copy
We still have a long rode to ride with or without `Unsafe`.
Our code copies a station to a byte array to look up in a map. 
Why? Let's eliminate one more copy:

```java
void loop(long position, long limit, Aggregates aggregates) {
    while (position < limit) {
        long start = position;  // address where station starts
        int length = 0;
        int hash = 0;
        {  // parse station
            for (byte b; (b = UNSAFE.getByte(position++)) != ';'; ) {
                length++;              // no copy
                hash = 71 * hash + b;
            }
        }
        
        // ... parse temperature
        Entry entry = aggregates.put(start, length, hash);
        // ... update
    }
}
```

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 |
| 09 | UnsafeParsing      |   3.861 ± 0.220 |           -8.51 |   6.035 ± 0.574 |           -8.88 |
| 10 | NoKeyCopy          |   3.416 ± 0.055 |          -11.51 |   5.207 ± 0.023 |          -13.72 |

Nice!

### 11 - SWAR Station
Here the real magic comes. Our code parses a station byte by byte. 
But applying bit tricks, it is possible to read 8 bytes at once to find ';' separator.
It is worth mentioning that most CPUs where we run our applications happen to be little-endian. 
Which means the bytes are inverted when we read 8 bytes from memory into a register. Example: `abcdefgh -> hgfedcba`.
All bit tricks working with 8-byte words should account for that.

```java
void loop(long position, long limit, Aggregates aggregates) {
    while (position < limit) {
        long start = position;
        long word;
        long hash = 0;
    
        while (true) {
            word = UNSAFE.getLong(position); // "Malaga;3" -> "3;agalaM"
            long match = word ^ 0x3B3B3B3B3B3B3B3BL; // constant: ";;;;;;;;"
            long comma = (match - 0x0101010101010101L) & (~match & 0x8080808080808080L); // 0x0080000000000000
   
            if (comma == 0) {               // no commas
                hash = 71 * hash + word;    // !!!hash function affected!!!
                position += 8;
                continue;
            }
   
            word &= (comma ^ (comma - 1));  // mask bytes after first comma, "3;agalaM" -> "\u0000;agalaM"
            hash = 71 * hash + word;        // !!!hash function affected!!!
            position += (Long.numberOfTrailingZeros(comma) >>> 3) + 1; // 6 + 1
            break;
        }
      
        int length = (int) (position - start);  // include comma
        // ... temperature
        Entry aggregate = aggregates.put(start, length, hash, word); // word: "\u0000;agalaM"
        // ... update
    }
}
```
You can find some explanation here: [link](https://richardstartin.github.io/posts/finding-bytes). Or you can read a book for true hackers: [Hacker's Delight](https://www.amazon.com/Hackers-Delight-2nd-Henry-Warren/dp/0321842685).

The idea of this trick is to reduce the number of instructions, branches and branch misses. 
Running perf stat proves it.

| Counter       | Before (413) |         After (413) | Reduction (413) |
|---------------|-------------:|--------------------:|----------------:|
| instructions  | 256026817170 |        125049604981 |          -51.15 |
| branches      |  33119837958 |         13451333400 |          -59.38 |
| branch-misses |   1530827426 |           822678362 |          -46.25 |

Let's test it out.

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 10 | NoKeyCopy          |   3.416 ± 0.055 |          -11.51 |   5.207 ± 0.023 |          -13.72 |
| 11 | SwarStation        |   2.059 ± 0.070 |          -39.74 |   7.042 ± 0.122 |           35.26 |

There are insane gains on the main dataset. But what the heck with the bonus dataset?
We damaged hash function by applying this change. Collecting collision stats proves it.

| #  | Change      | Stations 413 | Stations 10k |
|----|-------------|-------------:|-------------:|
| 06 | SimpleMap   |            3 |        ~1053 | 
| 11 | SwarStation |            8 |        ~3745 | 

We will fix it later, let's continue.

### 12 - SWAR Temperature
It is possible to parse temperature branchlessly:

```java
// Authour: merykitty
// Parse a number that may/may not contain a minus sign followed by a decimal with
// 1 - 2 digits to the left and 1 digits to the right of the separator to a
// fix-precision format. It returns the offset of the next line (presumably followed
// the final digit and a '\n')
long parseTemperature(long address) {
    long word = UNSAFE.getLong(address);
    // The 4th binary digit of the ascii of a digit is 1 while
    // that of the '.' is 0. This finds the decimal separator
    // The value can be 12, 20, 28
    int decimalSepPos = Long.numberOfTrailingZeros(~word & 0x10101000);
    int shift = 28 - decimalSepPos;
    // signed is -1 if negative, 0 otherwise
    long signed = (~word << 59) >> 63;
    long designMask = ~(signed & 0xFF);
    // Align the number to a specific position and transform the ascii code
    // to actual digit value in each byte
    long digits = ((word & designMask) << shift) & 0x0F000F0F00L;
    // Now digits is in the form 0xUU00TTHH00 (UU: units digit, TT: tens digit, HH: hundreds digit)
    // 0xUU00TTHH00 * (100 * 0x1000000 + 10 * 0x10000 + 1) =
    // 0x000000UU00TTHH00 +
    // 0x00UU00TTHH000000 * 10 +
    // 0xUU00TTHH00000000 * 100
    // Now TT * 100 has 2 trailing zeroes and HH * 100 + TT * 10 + UU < 0x400
    // This results in our value lies in the bit 32 to 41 of this product
    // That was close :)
    long absValue = ((digits * 0x640a0001) >>> 32) & 0x3FF;
    return (absValue ^ signed) - signed;
}
```

You can find explanation here: [link](https://questdb.io/blog/1brc-merykittys-magic-swar/).

The idea of this code to remove branches and take best of ILP (instruction-level parallelism). 
Executing more instructions without branches can be faster than executing less with branches taken evenly.
Branch mispredict hits hard.

| Counter       | Before (413) |  After (413) | Reduction (413) |
|---------------|-------------:|-------------:|----------------:|
| instructions  | 125049604981 | 135605896527 |           +8.44 |
| branches      |  13451333400 |  11207649114 |          -16.68 |
| branch-misses |    822678362 |    525966810 |          -36.06 |

Let's test it out.

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 11 | SwarStation        |   2.059 ± 0.070 |          -39.74 |   7.042 ± 0.122 |           35.26 |
| 12 | SwarTemperature    |   1.798 ± 0.021 |          -12.67 |   6.865 ± 0.167 |           -2.52 |

Nice!

### 13 - Better Hash
It is time to tune up our hash function.

```java
void loop(long position, long limit, Aggregates aggregates) {
    while (position < limit) {
        // ...
        long hash = 0;
  
        while (true) {  // 8-byte SWAR parsing for station
            // ...
            if (comma == 0) {  // no commas
                hash ^= word;  // ^ instead of *
                // ...
                continue;
            }
            // ...
            hash = mix(hash ^ word);
            // ...
            break;
        }
    }
}

long mix(long x) {  // mix bits
    long h = x * -7046029254386353131L; // constant taken from fastutils
    h ^= h >>> 35;  // tune shifting while looking at collision stats
    return h;
}
```

Checking collision stats shows an improvement:

| #  | Change      | Stations 413 | Stations 10k |
|----|-------------|-------------:|-------------:|
| 06 | SimpleMap   |            3 |        ~1053 | 
| 11 | SwarStation |            8 |        ~3745 | 
| 13 | BetterHash  |            1 |         ~712 |  

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 12 | SwarTemperature    |   1.798 ± 0.021 |          -12.67 |   6.865 ± 0.167 |           -2.52 |
| 13 | BetterHash         |   1.776 ± 0.038 |           -1.22 |   2.698 ± 0.078 |          -60.70 |

Nice! The bonus dataset starts feeling well.

### 14 - Better Map
Let's analyze the dereference chain to access Key array.

```java
entries[entryIndex] ->jump-> entry ->jump-> array[byteIndex]
```
We chase pointers two times. Instead, we can inline `station/length/hash/min/max/sum/count` into entries array.
Entry layout:

* 0-4    - length
* 4-8    - hash
* 8-16   - sum
* 16-20  - count
* 20-22  - min
* 22-24  - max
* 24-128 - station

Each entry has 128-byte length. It fits into two 64-byte cache lines. If station length <= 40 it fits into one cache line.
Each entry of the main dataset fits into 1 cache line, 413 cache lines in total, which fits in L1 cache completely.
The map capacity is 128 * 64 KB = 8 MB. Not a big deal, we only traverse the whole map at the end when merging.

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 13 | BetterHash         |   1.776 ± 0.038 |           -1.22 |   2.698 ± 0.078 |          -60.70 |
| 14 | BetterMap          |   1.727 ± 0.043 |           -2.77 |   2.179 ± 0.029 |          -19.23 |

Nice! It improves performance more on the bonus dataset where L2/L3 caches are engaged.

### 15 - Parallelism Sharing
Let's get back on how we parallelize. We split a file into N even chunks. Run N threads and then merge the results into one map.
That works under two conditions:
 * Even data - same data in chunks on average.
 * No cpu contention - our threads are the only ones running.

In the real life you won't see these conditions are met unless you are working in HFT and apply tuning tweaks - both hardware and software.
Usually you spin your k8s cluster and keep deploying pods. As well as the data is not that even.
So some threads are naturally lagging behind. And our program is waiting for the slowest thread to complete.
Instead, let's divide a file into M small segments and put them into a queue, so our threads can take them and process.
Instead of a queue, let's simply use a counter for the current segment and that's it.

```java
void run(AtomicInteger counter, int segmentCount) {
    for (int segment; (segment = counter.getAndIncrement()) < segmentCount; ) {
        long position = SEGMENT_SIZE * segment;  // segment start
        long size = Math.min(SEGMENT_SIZE + 1, fileSize - position);
        long limit = position + size; // segment end

        if (segment > 0) {
            position = nextLine(position); // find next whole line
        }

        process(position, limit); // gooo!
    }
}
```

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 14 | BetterMap          |   1.727 ± 0.043 |           -2.77 |   2.179 ± 0.029 |          -19.23 |
| 15 | ParallelismSharing |   1.728 ± 0.020 |            0.05 |   2.211 ± 0.031 |            1.47 |

Nothing? Yes, it does not improve the results at this point. 
Because the threads finish at the same time in such isolated environment.
But let's keep it till the end. Maybe something will happen.

### 16 - Parallelism Merging
It is possible to merge the results in parallel.

```java
void run(AtomicReference<Aggregates> result) {
    Aggregates aggregates = new Aggregates();
    // ...process

    while (!result.compareAndSet(null, aggregates)) {
        Aggregates rights = result.getAndSet(null);
        
        if (rights != null) {
            aggregates.merge(rights);
        }
    }
}
```

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 15 | ParallelismSharing |   1.728 ± 0.020 |            0.05 |   2.211 ± 0.031 |            1.47 |
| 16 | ParallelismMerging |   1.725 ± 0.015 |           -0.13 |   2.218 ± 0.030 |            0.31 |

It does not make a difference. The optimization will work when there are a lot of threads and bigger maps. Let's keep it anyway.

### 17 - Graal JIT
I heard about GraalVM. Let's try it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 16 | ParallelismMerging |   1.725 ± 0.015 |           -0.13 |   2.218 ± 0.030 |            0.31 |
| 17 | Graal JIT          |   1.697 ± 0.033 |           -1.66 |   2.178 ± 0.023 |           -1.80 |

At this point it slightly improves the results.

### 18 - Graal AOT
GraalVM comes with `native-image` which can compile our program into native executable image. 
The main gains we expect from eliminating startup cost of JVM.
I had really hard time to get it right. Big thanks to Thomas Wuerthinger.
Let's try these settings:

```shell
--gc=epsilon -O3 -march=native -R:MaxHeapSize=64m --enable-preview
```
We use heap only to sort and print the result. So no GC is needed and 64 MB heap is enough.

```shell
--initialize-at-build-time=$CLASS_NAME
```
We want to initialize our class at build time to initialize UNSAFE field to eliminate NPE checks at runtime.
Otherwise, it performs slower than Graal JIT.

```shell
-H:-GenLoopSafepoints
```
We do not need safepoint checks in our long loops.

```shell
-H:TuneInlinerExploration=1
```
We do want to inline more. This one will help later with ILP loop.

Let's check it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 17 | Graal JIT          |   1.697 ± 0.033 |           -1.66 |   2.178 ± 0.023 |           -1.80 |
| 18 | Graal AOT          |   1.433 ± 0.002 |          -15.52 |   1.860 ± 0.001 |          -14.62 |

Insane! The more detailed breakdown:

| #    | Change                                            |              Time (413) |
|------|---------------------------------------------------|------------------------:|
| 17   | Graal JIT                                         |           1.697 ± 0.033 |
| 18.1 | Graal AOT: --gc=epsilon -R:MaxHeapSize=64m        |           1.861 ± 0.002 |    
| 18.2 | Graal AOT: --initialize-at-build-time=$CLASS_NAME |           1.486 ± 0.002 |
| 18.3 | Graal AOT: -H:-GenLoopSafepoints                  |           1.444 ± 0.003 |
| 18.4 | Graal AOT: -H:TuneInlinerExploration=1            |           1.433 ± 0.002 |

### 19 - Branchy Min/Max
The other side of branchless code is that, it is usually more heavy than branchy code. 
If the branches are taken rarely or frequently, they can perform better.
It gives us an idea that we can try using branches to compute min/max for temperatures instead of `Math.min` and `Math.max` methods.

```java
void update(long entry, long value) {
    long sum = UNSAFE.getLong(entry + 8) + value;
    int cnt = UNSAFE.getInt(entry + 16) + 1;
    short min = UNSAFE.getShort(entry + 20);
    short max = UNSAFE.getShort(entry + 22);

    UNSAFE.putLong(entry + 8, sum);
    UNSAFE.putInt(entry + 16, cnt);

    if (value < min) {  // instead of Math.min
        UNSAFE.putShort(entry + 20, (short) value);
    }

    if (value > max) { // instead of Math.max
        UNSAFE.putShort(entry + 22, (short) value);
    }
}
```

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 |
| 18 | Graal AOT          |   1.433 ± 0.002 |          -15.52 |   1.860 ± 0.001 |          -14.62 |
| 19 | BranchyMinMax      |   1.415 ± 0.002 |           -1.31 |   1.820 ± 0.002 |           -2.15 |

Nice!

### 20 - Branchy 08 Loop
All these branches and the fact, that the main dataset contains 97.5% of stations with length 0-15, give us an idea to try writing a branchy loop.
We will split it into three cases: 0-7, 8-15, 16+.

```java
void loop(long position, long limit, Aggregates aggregates) {
    while (position < limit) {
        long word = UNSAFE.getLong(position); // the first 8 bytes
        long comma = comma(word);

        if (comma != 0) { // 0-7 case: 52.0 %
            // hash, length, word
            // temperature
            // lookup and update if found
            if (found) {
                continue;
            }
        } else {
            word = UNSAFE.getLong(position + 8); // the second 8 bytes
            comma = comma(word);

            if (comma != 0) { // 8-15 case: 45.5 %
                // hash, length, word
                // temperature
                // lookup and update if found
                if (found) {
                    continue;
                }
            } else {  // 16+: 2.5 %
                // handle 16+ case
            }
        }

        // ~2.5 %
        long pointer = aggregates.put(position, word, length, hash);
        Aggregates.update(pointer, value);
        position = end;
    }
}
```

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 19 | BranchyMinMax      |   1.415 ± 0.002 |           -1.31 |   1.820 ± 0.002 |           -2.15 |
| 20 | Branchy08Loop      |   1.372 ± 0.002 |           -3.03 |   1.871 ± 0.003 |            2.80 |

Nice.

### 21 - Branchy 16 Loop
The first two branches are taken evenly. How can we merge them into one branch?

```java
static final long[] MASK = {0, 0, 0, 0, 0, 0, 0, 0, -1};

void loop(long position, long limit, Aggregates aggregates) {
    while (position < limit) {
        long word1 = UNSAFE.getLong(position);
        long word2 = UNSAFE.getLong(position + 8);
        long comma1 = comma(word1);
        long comma2 = comma(word2);

        if ((comma1 | comma2) != 0) { // 0-15 case: 97.5 %
            int length1 = length(comma1);
            long mask2 = MASK[length1];             // 0x0000000000000000 if length1 < 8, otherwise 0xFFFFFFFFFFFFFFFF
            long length2 = length(comma2) & mask2;  // zero length2 if length1 < 8
                
            word1 = mask(word1, comma1);
            word2 = mask(word2 & mask2, comma2);    // zero word2 if length1 < 8
               
            // hash, temperature
            // lookup and update if found
            if (found) {
                continue;
            }
        } else { // 16+: 2.5 %
            // handle 16+ case
        }

        // ~2.5 %
        long pointer = aggregates.put(position, word1, length, hash);
        Aggregates.update(pointer, value);
        position = end;
    }
}
```
The code uses a lookup table to get a mask for 0-8 length. The length is 8 when a word does not contain ';'.
The mask zeros the second word if ';' is found in the first word, otherwise the second word is left untouched.
This code takes more instructions to complete, but it does reduce branch misses dramatically.

| Counter           |     Before (413) |       After (413) | Reduction(413) |
|-------------------|-----------------:|------------------:|---------------:|
| cycles            |      33858380246 |       33551654211 |                |
| instructions      |      90120047666 |      108685743993 |         +20.60 |
| instruction/cycle |             2.66 |              3.24 |                |
| branches          |       8131257572 |        9013059309 |                | 
| branch-misses     |        532076849 |          30633096 |         -94.24 |
| miss/branch %     |            6.54% |             0.34% |                |

Almost 0.5 million branch misses are gone. We missed each second row. Let's test it out.

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 |
| 20 | Branchy08Loop      |   1.372 ± 0.002 |           -3.03 |   1.871 ± 0.003 |            2.80 |
| 21 | Branchy16Loop      |   1.332 ± 0.002 |           -2.87 |   1.995 ± 0.002 |            6.64 |

Nice! The program executes more instructions but finishes faster.

### 22 - CMOV
I came on the 1st of February and looked into the code and found one more trick I wanted to test. 
Look at the lookup table. It contains a lot of 0's and only one -1. So we can replace:

```java
   long[] MASK = {0, 0, 0, 0, 0, 0, 0, 0, -1};
   int mask1 = MASK[length1];
   // replace with
   int mask2 = (length1 == 8) ? -1 : 0; // or (comma1 == 0) ? -1 : 0; 
```
LOL, it is one more branch. We have been working so hard to eliminate them.
Not really, it will be compiled into conditional move (cmov). 
Back then, I was thinking of why do we read memory at all, it should help to reduce latency of reads. Let's test it out.

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 |
| 21 | Branchy16Loop      |   1.332 ± 0.002 |           -2.87 |   1.995 ± 0.002 |            6.64 |
| 22 | CMOV               |   1.246 ± 0.002 |           -6.50 |   1.931 ± 0.002 |           -3.22 |

You can find more information on cmov: [link](https://shipilev.net/jvm/anatomy-quarks/30-conditional-moves/).

Indeed, it improves things quite a bit at this stage. But no one said that it will improve things at the end :(

### 23 - ILP

We got rid of the branches. Now it is time to take the best of instruction-level parallelism (ILP). 
CPU has a quite complex pipeline and is able to be executing several independent instructions at the same time.
How do we parallelize instructions? We already squeezed a lot out of one row. Can we process several rows in one loop?
Yes, we can split our segment in 3 chunks and write a loop to process 3 chunks at once.

```java
void loop(long position, long limit, Aggregates aggregates) {
    // ...split into three chunks: chunk1, chunk2, chunk3 
    while (chunk1.has() && chunk2.has() && chunk3.has()) {
        long word1 = UNSAFE.getLong(chunk1.position);
        long word2 = UNSAFE.getLong(chunk1.position + 8);
        long word3 = UNSAFE.getLong(chunk2.position);
        long word4 = UNSAFE.getLong(chunk2.position + 8);
        long word5 = UNSAFE.getLong(chunk3.position);
        long word6 = UNSAFE.getLong(chunk3.position + 8);

        long comma1 = comma(word1);
        long comma2 = comma(word2);
        long comma3 = comma(word3);
        long comma4 = comma(word4);
        long comma5 = comma(word5);
        long comma6 = comma(word6);

        long entry1 = find(aggregates, chunk1, word1, word2, comma1, comma2);
        long entry2 = find(aggregates, chunk2, word3, word4, comma3, comma4);
        long entry3 = find(aggregates, chunk3, word5, word6, comma5, comma6);

        long temperature1 = temperature(chunk1);
        long temperature2 = temperature(chunk2);
        long temperature3 = temperature(chunk3);

        Aggregates.update(entry1, temperature1);
        Aggregates.update(entry2, temperature2);
        Aggregates.update(entry3, temperature3);
    }
    // process tail for chunk1, chunk2, chunk3
}
```

Let's run perf stat:

| Counter           | Before (413) |       After (413) | Reduction (413) |
|-------------------|-------------:|------------------:|----------------:|
| cycles            |  28887862039 |       22514924625 |          -22.06 |
| instructions      | 111201298567 |      102418710802 |           -7.89 |
| instruction/cycle |         3.85 |              4.55 |          +18.18 |
| branches          |   7914621619 |        8420111564 |                 |
| branch-misses     |     30474171 |          28630062 |                 |
| miss/branch %     |        0.39% |             0.34% |                 |

Now IPC is higher. It is really hard to get it right. It depends on CPU architecture. 
It can cause register spilling when a function runs out of registers and spills data from 64-bit registers to stack or 128+ bit registers.
You can always look at the generated assembly to check it. But if you know an easier way of doing it, please let me know.

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 22 | CMOV               |   1.246 ± 0.002 |           -6.50 |   1.931 ± 0.002 |           -3.22 |
| 23 | ILP                |   0.964 ± 0.002 |          -22.64 |   1.788 ± 0.001 |           -7.39 |

Wow, that is insane! We broke 1 second boundary at this machine.

### 24 - Subprocess

The last, but not the least, optimization is related to unmaping a file. 
The munmap call takes around 150-200 ms and cannot be parallelized, because it takes a process-wide lock.
There are several options of how to mitigate this cost:
 * Coming back to plain IO and copy pages to user space - copy overhead.
 * Having main thread to unmap pages while the processing threads are working - cpu contention.
 * Having a subprocess to return the result and start unmaping in the background - an orphan left alone :(

We go with the option #3:

```java
void spawn() {
    ProcessHandle.Info info = ProcessHandle.current().info();
    ArrayList<String> commands = new ArrayList<>();
    Optional<String> command = info.command();
    Optional<String[]> arguments = info.arguments();

    command.ifPresent(commands::add);
    arguments.ifPresent(strings -> commands.addAll(Arrays.asList(strings)));
    commands.add("--worker");

    new ProcessBuilder()
                .command(commands)
                .start()
                .getInputStream()
                .transferTo(System.out);
}

void main() {
    // spawn a subprocess if not a worker
    
    if (worker) {
        // process rows
        output.println(result);
        output.close();
    }
}
```

Let's test it out:

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 |
| 23 | ILP                |   0.964 ± 0.002 |          -22.64 |   1.788 ± 0.001 |           -7.39 |
| 24 | Subprocess         |   0.834 ± 0.001 |          -13.45 |   1.627 ± 0.003 |           -9.01 |

Nice, finally we see the end of the road. We have got x150 improvement vs Baseline.

### Original Solution 

Let's do some quick experiments. The original solution does not have CMOV optimization. Let's test it out.

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 24 | Subprocess         |   0.834 ± 0.001 |          -13.45 |   1.627 ± 0.003 |           -9.01 |
| 97 | Original           |   0.835 ± 0.002 |            0.09 |   1.635 ± 0.001 |            0.49 |

Nothing, it is still a little bit different code. Let's change the original solution to have CMOV optimization.

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 97 | Original           |   0.835 ± 0.002 |            0.09 |   1.635 ± 0.001 |            0.49 |
| 98 | Original + CMOV    |   0.836 ± 0.005 |            0.11 |   1.638 ± 0.001 |            0.19 |

Nothing, it does not seem to help at this stage. Why? I have not figured it out. The answer lies hidden in CPU pipeline.

On the other hand, it does improve the result on ~11% on Apple M1 Pro :)

And the last experiment, let's remove work sharing optimizations #15 and #16 from the original solution.

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 97 | Original           |   0.835 ± 0.002 |            0.09 |   1.635 ± 0.001 |            0.49 |
| 99 | Original - Sharing |   0.914 ± 0.020 |            9.48 |   1.709 ± 0.021 |            4.55 |

LOL, when I run it once I got much better results. But when I run it one by one I see stable degradation.
Let's collect the time when our runners come to the finish line.

Before the change:

| Place | Run #1 (413) | Run #2 (413) |
|-------|-------------:|-------------:|
| 1     |    1st place |    1st place |
| 2     |            0 |            0 |
| 3     |            0 |            0 |
| 4     |            0 |            0 |
| 5     |            0 |            0 |
| 6     |            0 |            0 |
| 7     |           +1 |            0 |
| 8     |           +1 |            0 |

And after the change:

| Place | Run #1 (413) | Run #2 (413) |
|-------|-------------:|-------------:|
| 1     |    1st place |    1st place |
| 2     |           +1 |            0 |
| 3     |           +1 |            0 |
| 4     |           +1 |           +1 |
| 5     |           +1 |           +2 |
| 6     |           +1 |           +3 |
| 7     |           +2 |          +87 |
| 8     |           +4 |          +92 |

Something went wrong with the second run without work sharing optimization. 
Can you guess what it is?
The answer is simple. The orphan left from the first run is still unmapping - stealing our CPUs.
So the original solution finishes faster because it is more resistant against CPU contention.

# Conclusions
I hope you really enjoyed riding this really long road and learnt something new. 
As for me, I learnt a lot of tricks how to squeeze every drop of performance out of a Java program.
And now it does not seem like a month is enough for this challenge. You can always do better.

# Acknowledgements
Thank you guys very much:

* @gunnarmorling for the challenge, a mug and a t-shirt:)
* @thomaswue for the best ILP loop, branchy loop and a lot of hints.
* @merykitty for the parsing temperatures with SWAR trick.
* @royvanrijn for the parsing stations with SWAR trick.
* @jerrinot for the ILP and 16-byte branching.
* @abeobk for the masking idea.

All these ideas were incorporated in my final solution.