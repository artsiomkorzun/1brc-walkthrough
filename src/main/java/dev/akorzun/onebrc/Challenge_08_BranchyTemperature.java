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

import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import static dev.akorzun.onebrc.Challenge.round;


public class Challenge_08_BranchyTemperature implements Challenge {

    public static void main(String[] args) {
        new Challenge_08_BranchyTemperature().run(args);
    }

    @Override
    public void solve(String[] args, Path file, PrintStream output) throws Exception {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), Arena.global());
            Aggregator[] aggregators = split(segment);

            for (Aggregator aggregator : aggregators) {
                aggregator.start();
            }

            Map<String, Aggregate> result = new TreeMap<>(); // for sorting and output

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

    static void loop(MemorySegment segment, long position, long limit, Key key, Aggregates aggregates) {
        while (position < limit) {
            {  // 1-byte parsing for stations
                key.length = 0;
                key.hash = 0;
                for (byte b; (b = segment.get(ValueLayout.JAVA_BYTE, position++)) != ';'; ) {
                    key.array[key.length++] = b;
                    key.hash = 71 * key.hash + b;  // eliminates one array read when inserting in map
                }
            }

            int temperature;  // can fit temperature * 10, e.g -12.3 -> -123
            {  // 1-byte parsing for temperatures: -##.#, -#.#, #.#, ##.#
                int sign = 1;
                byte b0 = segment.get(ValueLayout.JAVA_BYTE, position);

                if (b0 == '-') {
                    sign = -1;
                    b0 = segment.get(ValueLayout.JAVA_BYTE, ++position);
                }

                byte b1 = segment.get(ValueLayout.JAVA_BYTE, position + 1);

                if (b1 == '.') {  // #.#
                    byte b2 = segment.get(ValueLayout.JAVA_BYTE, position + 2);
                    temperature = sign * (10 * (b0 - '0') + (b2 - '0'));
                    position += 4;  // + \n
                } else {          // ##.#
                    byte b3 = segment.get(ValueLayout.JAVA_BYTE, position + 3);
                    temperature = sign * (100 * (b0 - '0') + (10 * (b1 - '0')) + (b3 - '0'));
                    position += 5;  // + \n
                }
            }

            Aggregate aggregate = aggregates.put(key);
            aggregate.min = Math.min(aggregate.min, temperature);
            aggregate.max = Math.max(aggregate.max, temperature);
            aggregate.sum += temperature;
            aggregate.cnt++;
        }
    }

    static void merge(Aggregates from, Map<String, Aggregate> to) {
        from.visit((key, left) -> {
            String station = key.toString();
            Aggregate right = to.get(station);

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
        final MemorySegment segment;
        final long position;
        final long limit;
        Aggregates aggregates;

        public Aggregator(MemorySegment segment, long position, long limit) {
            this.segment = segment;
            this.position = position;
            this.limit = limit;
        }

        @Override
        public void run() {
            Aggregates aggregates = new Aggregates();
            Key key = new Key();
            loop(segment, position, limit, key, aggregates);
            this.aggregates = aggregates;
        }
    }

    static class Aggregates { // keys <= 10K, no need to grow
        private final Key[] keys = new Key[64 * 1024];
        private final Aggregate[] values = new Aggregate[keys.length];

        Aggregate put(Key key) {
            // hash % mask == hash & mask, because map size is a power of two, so we can use this trick instead of heavy div
            for (int mask = keys.length - 1, index = mix(key.hash) & mask; ; index = (index + 1) & mask) {
                Key candidate = keys[index];

                if (candidate == null) {
                    Aggregate value = new Aggregate();
                    keys[index] = new Key(key);  // copy key
                    values[index] = value;
                    return value;
                }

                if (candidate.equals(key)) {
                    return values[index];
                }
            }
        }

        void visit(BiConsumer<Key, Aggregate> consumer) {
            for (int i = 0; i < keys.length; i++) {
                Key key = keys[i];

                if (key != null) {
                    consumer.accept(key, values[i]);
                }
            }
        }

        static int mix(int hash) {
            return hash ^ (hash >>> 16);
        }
    }

    static class Key {
        final byte[] array;
        int length;
        int hash;

        public Key() {
            array = new byte[128];
        }

        public Key(Key key) {
            this.array = Arrays.copyOf(key.array, key.length);
            this.length = array.length;
            this.hash = key.hash;
        }

        @Override
        public boolean equals(Object o) {
            Key key = (Key) o;
            return Arrays.equals(array, 0, length, key.array, 0, key.length);
        }

        @Override
        public String toString() {
            return new String(array, 0, length, StandardCharsets.UTF_8);
        }
    }

    static class Aggregate {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int cnt = 0;

        @Override
        public String toString() {
            return (min / 10.0) + "/" + round(sum / 10.0 / cnt) + "/" + (max / 10.0);
        }
    }
}
