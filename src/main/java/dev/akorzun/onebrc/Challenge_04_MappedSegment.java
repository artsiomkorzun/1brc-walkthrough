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


public class Challenge_04_MappedSegment implements Challenge {

    public static void main(String[] args) {
        new Challenge_04_MappedSegment().run(args);
    }

    @Override
    public void solve(String[] args, Path file, PrintStream output) throws Exception {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), Arena.global());
            Map<Key, Aggregate> aggregates = new HashMap<>();
            Key key = new Key();  // mutable key

            loop(segment, 0, segment.byteSize(), key, aggregates);

            TreeMap<String, Aggregate> result = new TreeMap<>(); // for sorting and output
            aggregates.forEach((k, v) -> result.put(k.toString(), v));
            output.println(result);
        }
    }

    static void loop(MemorySegment segment, long position, long limit, Key key, Map<Key, Aggregate> aggregates) {
        while (position < limit) {
            {  // 1-byte parsing for stations
                key.length = 0;
                for (byte b; (b = segment.get(ValueLayout.JAVA_BYTE, position++)) != ';'; ) {
                    key.array[key.length++] = b;
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

    static class Key {
        final byte[] array;
        int length;

        public Key() {
            array = new byte[128];
        }

        public Key(Key key) {
            this.array = Arrays.copyOf(key.array, key.length);
            this.length = array.length;
        }

        @Override
        public boolean equals(Object o) {
            Key key = (Key) o;
            return Arrays.equals(array, 0, length, key.array, 0, key.length);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            for (int i = 0; i < length; i++) {
                hash = 71 * hash + array[i];
            }
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
