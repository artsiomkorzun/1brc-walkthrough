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
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static dev.akorzun.onebrc.Challenge.round;


public class Challenge_05_HashWhileParsing implements Challenge {

    public static void main(String[] args) {
        new Challenge_05_HashWhileParsing().run(args);
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

    static void loop(MemorySegment segment, long position, long limit, Key key, Map<Key, Aggregate> aggregates) {
        while (position < limit) {
            {  // 1-byte parsing for stations
                key.length = 0;
                key.hash = 0;
                for (byte b; (b = segment.get(ValueLayout.JAVA_BYTE, position++)) != ';'; ) {
                    key.array[key.length++] = b;
                    key.hash = 71 * key.hash + b;  // eliminates one array read when inserting in map
                }
            }

            double temperature = 0.0;
            {  // 1-byte parsing for temperatures: -##.#, -#.#, #.#, ##.#
                int sign = 1;

                for (byte b; (b = segment.get(ValueLayout.JAVA_BYTE, position++)) != '\n'; ) {
                    if (b == '-') {
                        sign = -1;
                    } else if (b != '.') {
                        temperature = 10 * temperature + (b - '0');
                    }
                }
                temperature = sign * temperature / 10;
            }

            Aggregate aggregate = aggregates.get(key);
            if (aggregate == null) {  // miss, new key and aggregate need to be allocated
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

    static void merge(Map<Key, Aggregate> from, Map<String, Aggregate> to) {
        for (Map.Entry<Key, Aggregate> entry : from.entrySet()) {
            String key = entry.getKey().toString();
            Aggregate left = entry.getValue();
            Aggregate right = to.get(key);

            if (right == null) {
                to.put(key, left);
            } else {
                right.min = Math.min(left.min, right.min);
                right.max = Math.max(left.max, right.max);
                right.sum = left.sum + right.sum;
                right.cnt = left.cnt + right.cnt;
            }
        }
    }

    static class Aggregator extends Thread {
        final MemorySegment segment;
        final long position;
        final long limit;
        Map<Key, Aggregate> aggregates;

        public Aggregator(MemorySegment segment, long position, long limit) {
            this.segment = segment;
            this.position = position;
            this.limit = limit;
        }

        @Override
        public void run() {
            Map<Key, Aggregate> aggregates = new HashMap<>();
            Key key = new Key();
            loop(segment, position, limit, key, aggregates);
            this.aggregates = aggregates;
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
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return new String(array, 0, length, StandardCharsets.UTF_8);
        }
    }

    static class Aggregate {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        double cnt = 0.0;

        @Override
        public String toString() {
            return round(min) + "/" + round((Math.round(sum * 10.0) / 10.0) / cnt) + "/" + round(max);
        }
    }
}
