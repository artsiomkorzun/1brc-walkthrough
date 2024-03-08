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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public class Challenge_24_Subprocess implements Challenge {

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

    public static void main(String[] args) throws Exception {
        if (isSpawn(args)) {
            spawn();
            return;
        }

        new Challenge_24_Subprocess().run(args);
    }

    static boolean isSpawn(String[] args) {
        for (String arg : args) {
            if ("--worker".equals(arg)) {
                return false;
            }
        }

        return true;
    }

    static void spawn() throws Exception {
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

    @Override
    public void solve(String[] args, Path file, PrintStream output) throws Exception {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), Arena.global());
            long start = segment.address();
            long end = segment.address() + segment.byteSize();

            int parallelism = Runtime.getRuntime().availableProcessors();
            Aggregator[] aggregators = new Aggregator[parallelism];

            AtomicReference<Aggregates> result = new AtomicReference<>();
            AtomicLong cursor = new AtomicLong(start);

            for (int i = 0; i < parallelism; i++) {
                aggregators[i] = new Aggregator(result, cursor, start, end);
                aggregators[i].start();
            }

            for (Aggregator aggregator : aggregators) {
                aggregator.join();
            }

            output.println(result.get().build());
            output.close();
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
        long chunk = (limit - position) / 3;
        long left = next(position + chunk);
        long right = next(position + chunk + chunk);

        Chunk chunk1 = new Chunk(position, left);
        Chunk chunk2 = new Chunk(left, right);
        Chunk chunk3 = new Chunk(right, limit);

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

            long pointer1 = find(aggregates, chunk1, word1, word2, comma1, comma2);
            long pointer2 = find(aggregates, chunk2, word3, word4, comma3, comma4);
            long pointer3 = find(aggregates, chunk3, word5, word6, comma5, comma6);

            long value1 = value(chunk1);
            long value2 = value(chunk2);
            long value3 = value(chunk3);

            Aggregates.update(pointer1, value1);
            Aggregates.update(pointer2, value2);
            Aggregates.update(pointer3, value3);
        }

        while (chunk1.has()) {
            long word1 = UNSAFE.getLong(chunk1.position);
            long word2 = UNSAFE.getLong(chunk1.position + 8);

            long comma1 = comma(word1);
            long comma2 = comma(word2);

            long pointer = find(aggregates, chunk1, word1, word2, comma1, comma2);
            long value = value(chunk1);

            Aggregates.update(pointer, value);
        }

        while (chunk2.has()) {
            long word1 = UNSAFE.getLong(chunk2.position);
            long word2 = UNSAFE.getLong(chunk2.position + 8);

            long comma1 = comma(word1);
            long comma2 = comma(word2);

            long pointer = find(aggregates, chunk2, word1, word2, comma1, comma2);
            long value = value(chunk2);

            Aggregates.update(pointer, value);
        }

        while (chunk3.has()) {
            long word1 = UNSAFE.getLong(chunk3.position);
            long word2 = UNSAFE.getLong(chunk3.position + 8);

            long comma1 = comma(word1);
            long comma2 = comma(word2);

            long pointer = find(aggregates, chunk3, word1, word2, comma1, comma2);
            long value = value(chunk3);

            Aggregates.update(pointer, value);
        }
    }

    static long find(Aggregates aggregates, Chunk chunk, long word1, long word2, long comma1, long comma2) {
        long position = chunk.position;
        long hash;

        if ((comma1 | comma2) != 0) {
            long mask2 = (comma1 == 0) ? -1 : 0;  // cmov
            word1 = mask(word1, comma1);
            word2 = mask(word2 & mask2, comma2);
            hash = mix(word1 ^ word2);

            chunk.position += length(comma1) + (length(comma2) & mask2) + 1;
            long pointer = aggregates.find(word1, word2, hash);

            if (pointer != 0) {
                return pointer;
            }

            word1 = (comma1 == 0) ? word2 : word1;
        } else {
            chunk.position += 16;
            hash = word1 ^ word2;

            while (true) {
                word1 = UNSAFE.getLong(chunk.position);
                comma1 = comma(word1);

                if (comma1 == 0) {
                    chunk.position += 8;
                    hash ^= word1;
                    continue;
                }

                word1 = mask(word1, comma1);
                hash = mix(hash ^ word1);
                chunk.position += length(comma1) + 1;
                break;
            }
        }

        long length = chunk.position - position;
        return aggregates.put(position, word1, length, hash);
    }

    static long comma(long word) {
        long match = word ^ COMMA;
        return (match - 0x0101010101010101L) & (~match & 0x8080808080808080L);
    }

    static long mask(long word, long separator) {
        long mask = separator ^ (separator - 1);
        return word & mask;
    }

    static int length(long separator) {
        return (Long.numberOfTrailingZeros(separator) >>> 3);
    }

    static int dot(long num) {
        return Long.numberOfTrailingZeros(~num & DOT_BITS);
    }

    static long value(Chunk chunk) {
        long num = UNSAFE.getLong(chunk.position);
        long dot = dot(num);
        long signed = (~num << 59) >> 63;
        long mask = ~(signed & 0xFF);
        long digits = ((num & mask) << (28 - dot)) & 0x0F000F0F00L;
        long abs = ((digits * MAGIC_MULTIPLIER) >>> 32) & 0x3FF;
        long value = (abs ^ signed) - signed;
        chunk.position += (dot >> 3) + 3;
        return value;
    }

    static long mix(long x) {
        long h = x * -7046029254386353131L;
        h ^= h >>> 35;
        return h;
    }

    static class Aggregator extends Thread {
        final AtomicReference<Aggregates> result;
        final AtomicLong cursor;
        final long start;
        final long end;

        public Aggregator(AtomicReference<Aggregates> result, AtomicLong cursor, long start, long end) {
            this.result = result;
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

            while (!result.compareAndSet(null, aggregates)) {
                Aggregates rights = result.getAndSet(null);

                if (rights != null) {
                    aggregates.merge(rights);
                }
            }
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

        long find(long word1, long word2, long hash) {
            long address = pointer + offset(hash);
            long w1 = UNSAFE.getLong(address + 24);
            long w2 = UNSAFE.getLong(address + 32);
            return (word1 == w1) && (word2 == w2) ? address : 0;
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

            if (value < min) {
                UNSAFE.putShort(address + 20, (short) value);
            }

            if (value > max) {
                UNSAFE.putShort(address + 22, (short) value);
            }
        }

        void merge(Aggregates rights) {
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

    private static class Chunk {
        final long limit;
        long position;

        public Chunk(long position, long limit) {
            this.position = position;
            this.limit = limit;
        }

        boolean has() {
            return position < limit;
        }
    }
}
