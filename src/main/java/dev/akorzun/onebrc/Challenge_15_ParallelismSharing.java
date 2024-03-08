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
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;


public class Challenge_15_ParallelismSharing implements Challenge {

    private static final long SEGMENT = 2 * 1024 * 1024;   // 2 MB
    private static final long COMMA = 0x3B3B3B3B3B3B3B3BL; // ;;;;;;;;
    private static final long LINE = 0x0A0A0A0A0A0A0A0AL;  // /n/n/n/n/n/n/n/
    private static final long DOT_BITS = 0x10101000;
    private static final long MAGIC_MULTIPLIER = (100 * 0x1000000 + 10 * 0x10000 + 1);
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
        new Challenge_15_ParallelismSharing().run(args);
    }

    @Override
    public void solve(String[] args, Path file, PrintStream output) throws Exception {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), Arena.global());
            long start = segment.address();
            long end = segment.address() + segment.byteSize();

            int parallelism = Runtime.getRuntime().availableProcessors();
            Aggregator[] aggregators = new Aggregator[parallelism];
            AtomicLong cursor = new AtomicLong(start);

            for (int i = 0; i < parallelism; i++) {
                aggregators[i] = new Aggregator(cursor, start, end);
                aggregators[i].start();
            }

            Aggregates aggregates = null;

            for (Aggregator aggregator : aggregators) {
                aggregator.join();
                Aggregates rights = aggregator.aggregates;
                aggregates = (aggregates == null) ? rights : aggregates.merge(rights);
            }

            output.println(aggregates.build());
        }
    }

    static long next(long position) {
        while (true) {
            long word = UNSAFE.getLong(position);
            long match = word ^ LINE;
            long line = (match - 0x0101010101010101L) & (~match & 0x8080808080808080L);

            if (line == 0) {
                position += 8;
                continue;
            }

            return position + (Long.numberOfTrailingZeros(line) >>> 3) + 1;
        }
    }

    static void loop(long position, long limit, Aggregates aggregates) {
        while (position < limit) {
            long start = position;
            long word;
            long hash = 0;

            while (true) {  // 8-byte SWAR parsing for stations
                word = UNSAFE.getLong(position); // must be little endian
                long match = word ^ COMMA;
                long comma = (match - 0x0101010101010101L) & (~match & 0x8080808080808080L);

                if (comma == 0) {  // no commas
                    hash ^= word;
                    position += 8;
                    continue;
                }

                word &= (comma ^ (comma - 1));  // masks bytes after comma
                hash = mix(hash ^ word);
                position += (Long.numberOfTrailingZeros(comma) >>> 3) + 1; // length in bytes before comma
                break;
            }

            long length = position - start;  // includes comma
            long temperature;  // can fit temperature * 10, e.g -12.3 -> -123
            {  // 8-byte SWAR parsing for temperatures: -##.#, -#.#, #.#, ##.#
                long num = UNSAFE.getLong(position);
                long dot = Long.numberOfTrailingZeros(~num & DOT_BITS);
                long signed = (~num << 59) >> 63;
                long mask = ~(signed & 0xFF);
                long digits = ((num & mask) << (28 - dot)) & 0x0F000F0F00L;
                long abs = ((digits * MAGIC_MULTIPLIER) >>> 32) & 0x3FF;
                temperature = ((abs ^ signed) - signed);
                position += (dot >> 3) + 3;
            }

            long pointer = aggregates.put(start, word, length, hash);
            Aggregates.update(pointer, temperature);
        }
    }

    static long mix(long x) {
        long h = x * -7046029254386353131L;
        h ^= h >>> 35;
        return h;
    }

    static class Aggregator extends Thread {
        final AtomicLong cursor;
        final long start;
        final long end;
        Aggregates aggregates;

        public Aggregator(AtomicLong cursor, long start, long end) {
            this.cursor = cursor;
            this.start = start;
            this.end = end;
        }

        @Override
        public void run() {
            Aggregates aggregates = new Aggregates();

            for (long position; (position = cursor.getAndAdd(SEGMENT)) < end; ) {
                long limit = position + Math.min(end - position, SEGMENT + 1);

                if (position > start) {
                    position = next(position);
                }

                loop(position, limit, aggregates);
            }


            this.aggregates = aggregates;
        }
    }

    private record Aggregate(int min, int max, long sum, int cnt) {
        @Override
        public String toString() {
            return (min / 10.0) + "/" + Challenge.round(sum / 10.0 / cnt) + "/" + (max / 10.0);
        }
    }

    static class Aggregates {

        private static final long ENTRIES = 64 * 1024;
        private static final long SIZE = 128 * ENTRIES;
        private static final long MASK = (ENTRIES - 1) << 7;

        private final long pointer;

        public Aggregates() {
            long address = UNSAFE.allocateMemory(SIZE + 4096);
            pointer = (address + 4095) & (~4095);
            UNSAFE.setMemory(pointer, SIZE, (byte) 0);
        }

        long put(long reference, long word, long length, long hash) {
            for (long offset = offset(hash); ; offset = next(offset)) {
                long address = pointer + offset;
                if (equal(address + 24, reference, word, length)) {
                    return address;
                }

                int len = UNSAFE.getInt(address);
                if (len == 0) {
                    alloc(address, reference, length, hash);
                    return address;
                }
            }
        }

        static void update(long address, long value) {
            long sum = UNSAFE.getLong(address + 8) + value;
            int cnt = UNSAFE.getInt(address + 16) + 1;
            short min = UNSAFE.getShort(address + 20);
            short max = UNSAFE.getShort(address + 22);

            UNSAFE.putLong(address + 8, sum);
            UNSAFE.putInt(address + 16, cnt);
            UNSAFE.putShort(address + 20, (short) Math.min(min, value));
            UNSAFE.putShort(address + 22, (short) Math.max(max, value));
        }

        Aggregates merge(Aggregates rights) {
            for (long rightOffset = 0; rightOffset < SIZE; rightOffset += 128) {
                long rightAddress = rights.pointer + rightOffset;
                int length = UNSAFE.getInt(rightAddress);

                if (length == 0) {
                    continue;
                }

                int hash = UNSAFE.getInt(rightAddress + 4);

                for (long offset = offset(hash); ; offset = next(offset)) {
                    long address = pointer + offset;

                    if (equal(address + 24, rightAddress + 24, length)) {
                        long sum = UNSAFE.getLong(address + 8) + UNSAFE.getLong(rightAddress + 8);
                        int cnt = UNSAFE.getInt(address + 16) + UNSAFE.getInt(rightAddress + 16);
                        short min = (short) Math.min(UNSAFE.getShort(address + 20), UNSAFE.getShort(rightAddress + 20));
                        short max = (short) Math.max(UNSAFE.getShort(address + 22), UNSAFE.getShort(rightAddress + 22));

                        UNSAFE.putLong(address + 8, sum);
                        UNSAFE.putInt(address + 16, cnt);
                        UNSAFE.putShort(address + 20, min);
                        UNSAFE.putShort(address + 22, max);
                        break;
                    }

                    int len = UNSAFE.getInt(address);

                    if (len == 0) {
                        UNSAFE.copyMemory(rightAddress, address, length + 24);
                        break;
                    }
                }
            }

            return this;
        }

        Map<String, Aggregate> build() {
            TreeMap<String, Aggregate> set = new TreeMap<>();

            for (long offset = 0; offset < SIZE; offset += 128) {
                long address = pointer + offset;
                int length = UNSAFE.getInt(address);

                if (length != 0) {
                    byte[] array = new byte[length - 1];
                    UNSAFE.copyMemory(null, address + 24, array, Unsafe.ARRAY_BYTE_BASE_OFFSET, array.length);
                    String key = new String(array);

                    long sum = UNSAFE.getLong(address + 8);
                    int cnt = UNSAFE.getInt(address + 16);
                    short min = UNSAFE.getShort(address + 20);
                    short max = UNSAFE.getShort(address + 22);

                    Aggregate aggregate = new Aggregate(min, max, sum, cnt);
                    set.put(key, aggregate);
                }
            }

            return set;
        }

        static void alloc(long address, long position, long length, long hash) {
            UNSAFE.putInt(address, (int) length);
            UNSAFE.putInt(address + 4, (int) hash);
            UNSAFE.putShort(address + 20, Short.MAX_VALUE);
            UNSAFE.putShort(address + 22, Short.MIN_VALUE);
            UNSAFE.copyMemory(position, address + 24, length);
        }

        static long offset(long hash) {
            return hash & MASK;
        }

        static long next(long prev) {
            return (prev + 128) & (SIZE - 1);
        }

        static boolean equal(long address, long position, long word, long length) {
            while (length > 8) {
                long left = UNSAFE.getLong(position);
                long right = UNSAFE.getLong(address);

                if (left != right) {
                    return false;
                }

                position += 8;
                address += 8;
                length -= 8;
            }

            return word == UNSAFE.getLong(address);
        }

        static boolean equal(long leftAddress, long rightAddress, long length) {
            do {
                long left = UNSAFE.getLong(leftAddress);
                long right = UNSAFE.getLong(rightAddress);

                if (left != right) {
                    return false;
                }

                leftAddress += 8;
                rightAddress += 8;
                length -= 8;
            } while (length > 0);

            return true;
        }
    }
}
