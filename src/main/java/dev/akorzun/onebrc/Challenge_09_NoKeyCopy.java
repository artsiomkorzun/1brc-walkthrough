/*
 *  Copyright 2023 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package dev.akorzun.onebrc;

import sun.misc.Unsafe;

import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static dev.akorzun.onebrc.Challenge.round;


public class Challenge_09_NoKeyCopy implements Challenge {

    private static final Unsafe UNSAFE;

    static {
        try {
            Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
            unsafe.setAccessible(true);
            UNSAFE = (Unsafe) unsafe.get(Unsafe.class);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new Challenge_09_NoKeyCopy().run(args);
    }

    @Override
    public void solve(String[] args, Path file, PrintStream output) throws Exception {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), Arena.global());
            Aggregator[] aggregators = split(segment);

            for (Aggregator aggregator : aggregators) {
                aggregator.start();
            }

            Map<String, Entry> result = new TreeMap<>(); // for sorting and output

            for (Aggregator aggregator : aggregators) {
                aggregator.join();
                merge(aggregator.aggregates, result);
            }

            output.println(result);
        }
    }

    static Aggregator[] split(MemorySegment segment) {
        int parallelism = Runtime.getRuntime().availableProcessors();
        long size = segment.byteSize();
        long chunk = (size + parallelism - 1) / parallelism;

        Aggregator[] aggregators = new Aggregator[parallelism];
        long position = 0;

        for (int i = 0; i < parallelism; i++) {
            long limit = next(segment, Math.min(position + chunk, size));
            aggregators[i] = new Aggregator(segment, position, limit);
            position = limit;
        }

        return aggregators;
    }

    static long next(MemorySegment segment, long position) {
        while (position < segment.byteSize() && segment.get(ValueLayout.JAVA_BYTE, position++) != '\n') {
        }

        return position;
    }

    static void loop(long position, long limit, Aggregates aggregates) {
        while (position < limit) {
            long start = position;
            int length = 0;
            int hash = 0;
            {  // 1-byte parsing for stations
                for (byte b; (b = UNSAFE.getByte(position++)) != ';'; ) {
                    length++;              // eliminates one array copy
                    hash = 71 * hash + b;  // eliminates one array read when inserting in map
                }
            }

            int temperature;  // can fit temperature * 10, e.g -12.3 -> -123
            {  // 1-byte parsing for temperatures: -##.#, -#.#, #.#, ##.#
                int sign = 1;
                byte b0 = UNSAFE.getByte(position);

                if (b0 == '-') {
                    sign = -1;
                    b0 = UNSAFE.getByte(++position);
                }

                byte b1 = UNSAFE.getByte(position + 1);

                if (b1 == '.') {  // #.#
                    byte b2 = UNSAFE.getByte(position + 2);
                    temperature = sign * (10 * (b0 - '0') + (b2 - '0'));
                    position += 4;  // + \n
                } else {          // ##.#
                    byte b3 = UNSAFE.getByte(position + 3);
                    temperature = sign * (100 * (b0 - '0') + (10 * (b1 - '0')) + (b3 - '0'));
                    position += 5;  // + \n
                }
            }

            Entry aggregate = aggregates.put(start, length, hash);
            aggregate.min = Math.min(aggregate.min, temperature);
            aggregate.max = Math.max(aggregate.max, temperature);
            aggregate.sum += temperature;
            aggregate.cnt++;
        }
    }

    static void merge(Aggregates from, Map<String, Entry> to) {
        from.visit((left) -> {
            String station = left.key();
            Entry right = to.get(station);

            if (right == null) {
                to.put(station, left);
            } else {
                right.min = Math.min(left.min, right.min);
                right.max = Math.max(left.max, right.max);
                right.sum = left.sum + right.sum;
                right.cnt = left.cnt + right.cnt;
            }
        });
    }

    static class Aggregator extends Thread {
        final long position;
        final long limit;
        Aggregates aggregates;

        public Aggregator(MemorySegment segment, long position, long limit) {
            this.position = segment.address() + position;
            this.limit = segment.address() + limit;
        }

        @Override
        public void run() {
            Aggregates aggregates = new Aggregates();
            loop(position, limit, aggregates);
            this.aggregates = aggregates;
        }
    }

    static class Aggregates { // keys <= 10K, no need to grow
        private final Entry[] entries = new Entry[64 * 1024];

        Entry put(long position, int length, int hash) {
            // hash % mask == hash & mask, because map size is a power of two, so we can use this trick instead of heavy div
            for (int mask = entries.length - 1, index = hash & mask; ; index = (index + 1) & mask) {
                Entry candidate = entries[index];

                if (candidate == null) {
                    Entry value = new Entry(position, length);  // copy key
                    entries[index] = value;
                    return value;
                }

                if (candidate.equals(position, length)) {
                    return entries[index];
                }
            }
        }

        void visit(Consumer<Entry> consumer) {
            for (Entry entry : entries) {
                if (entry != null) {
                    consumer.accept(entry);
                }
            }
        }
    }

    static class Entry {
        final byte[] key;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int cnt = 0;

        Entry(long address, int length) {
            key = new byte[length];
            UNSAFE.copyMemory(null, address, key, Unsafe.ARRAY_BYTE_BASE_OFFSET, length);
        }

        String key() {
            return new String(key, StandardCharsets.UTF_8);
        }

        boolean equals(long address, int length) {
            if (key.length != length) {
                return false;
            }

            for (int i = 0; i < length; i++) {
                if (key[i] != UNSAFE.getByte(address + i)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public String toString() {
            return (min / 10.0) + "/" + round(sum / 10.0 / cnt) + "/" + (max / 10.0);
        }
    }
}
