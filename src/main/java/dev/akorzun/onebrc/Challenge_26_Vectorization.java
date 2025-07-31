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

import jdk.incubator.vector.*;
import sun.misc.Unsafe;

import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class Challenge_26_Vectorization implements Challenge {

    private static final ByteOrder BO = ByteOrder.nativeOrder();
    private static final VectorSpecies<Byte> BV256 = ByteVector.SPECIES_256;

    private static final int VECTOR_SIZE = BV256.vectorByteSize();
    private static final MemorySegment MEMORY = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);

    private static final long SEGMENT = 2 * 1024 * 1024;   // 2 MB
    private static final long LINE = 0x0A0A0A0A0A0A0A0AL;  // /n/n/n/n/n/n/n/
    private static final long DOT_BITS = 0x10101000;
    private static final long MAGIC_MULTIPLIER = (100 * 0x1000000 + 10 * 0x10000 + 1);
    private static final byte SEMICOLON = ';';
    private static final Unsafe UNSAFE;

    private static final byte[] MASKS = {
            -1,
            -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
    };

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

        new Challenge_26_Vectorization().run(args);
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

    static void loop(Aggregates aggregates, long position1, long limit4) {
        long chunk = (limit4 - position1) / 4;
        long limit1 = next(position1 + chunk);
        long limit2 = next(position1 + chunk + chunk);
        long limit3 = next(position1 + chunk + chunk + chunk);

        long position2 = limit1;
        long position3 = limit2;
        long position4 = limit3;

        while (position1 < limit1 && position2 < limit2 && position3 < limit3 && position4 < limit4) {
            ByteVector vector1 = ByteVector.fromMemorySegment(BV256, MEMORY, position1, BO);
            ByteVector vector2 = ByteVector.fromMemorySegment(BV256, MEMORY, position2, BO);
            ByteVector vector3 = ByteVector.fromMemorySegment(BV256, MEMORY, position3, BO);
            ByteVector vector4 = ByteVector.fromMemorySegment(BV256, MEMORY, position4, BO);

            int length1 = vector1.eq(SEMICOLON).firstTrue() + 1;
            int length2 = vector2.eq(SEMICOLON).firstTrue() + 1;
            int length3 = vector3.eq(SEMICOLON).firstTrue() + 1;
            int length4 = vector4.eq(SEMICOLON).firstTrue() + 1;

            ByteVector mask1 = ByteVector.fromArray(BV256, MASKS, 33 - length1);
            ByteVector mask2 = ByteVector.fromArray(BV256, MASKS, 33 - length2);
            ByteVector mask3 = ByteVector.fromArray(BV256, MASKS, 33 - length3);
            ByteVector mask4 = ByteVector.fromArray(BV256, MASKS, 33 - length4);

            vector1 = vector1.and(mask1);
            vector2 = vector2.and(mask2);
            vector3 = vector3.and(mask3);
            vector4 = vector4.and(mask4);

            long hash1 = Aggregates.hash(vector1.reinterpretAsLongs().lane(0) ^ vector1.reinterpretAsLongs().lane(1));
            long hash2 = Aggregates.hash(vector2.reinterpretAsLongs().lane(0) ^ vector2.reinterpretAsLongs().lane(1));
            long hash3 = Aggregates.hash(vector3.reinterpretAsLongs().lane(0) ^ vector3.reinterpretAsLongs().lane(1));
            long hash4 = Aggregates.hash(vector4.reinterpretAsLongs().lane(0) ^ vector4.reinterpretAsLongs().lane(1));

            long entry1;
            if (length1 <= VECTOR_SIZE) {
                long result;
                // IDE-inlined Aggregates.putShort()
                long address = aggregates.pointer + hash1;
                ByteVector other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                if (vector1.compare(VectorOperators.EQ, other).allTrue()) {
                    result = address;
                } else {
                    for (long offset = hash1; ; offset = Aggregates.next(offset)) {
                        address = aggregates.pointer + offset;
                        other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                        if (vector1.compare(VectorOperators.EQ, other).allTrue()) {
                            result = address;
                            break;
                        }

                        if (UNSAFE.getInt(address) == 0) {
                            Aggregates.alloc(address, position1, length1, hash1);
                            result = address;
                            break;
                        }
                    }
                }

                entry1 = result;
            } else {
                length1 = findLong(position1);
                entry1 = aggregates.putLong(position1, length1, hash1);
            }

            // no vectorization, so keep them close to the branch
            position1 += length1;
            long value1 = UNSAFE.getLong(position1);
            long dot1 = Long.numberOfTrailingZeros(~value1 & DOT_BITS);
            position1 += (dot1 >> 3) + 3;
            value1 = value(value1, dot1);
            Aggregates.update(entry1, value1);

            long entry2;
            if (length2 <= VECTOR_SIZE) {
                long result;
                // IDE-inlined Aggregates.putShort()
                long address = aggregates.pointer + hash2;
                ByteVector other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                if (vector2.compare(VectorOperators.EQ, other).allTrue()) {
                    result = address;
                } else {
                    for (long offset = hash2; ; offset = Aggregates.next(offset)) {
                        address = aggregates.pointer + offset;
                        other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                        if (vector2.compare(VectorOperators.EQ, other).allTrue()) {
                            result = address;
                            break;
                        }

                        if (UNSAFE.getInt(address) == 0) {
                            Aggregates.alloc(address, position2, length2, hash2);
                            result = address;
                            break;
                        }
                    }
                }

                entry2 = result;
            } else {
                length2 = findLong(position2);
                entry2 = aggregates.putLong(position2, length2, hash2);
            }

            // no vectorization, so keep them close to the branch
            position2 += length2;
            long value2 = UNSAFE.getLong(position2);
            long dot2 = Long.numberOfTrailingZeros(~value2 & DOT_BITS);
            position2 += (dot2 >> 3) + 3;
            value2 = value(value2, dot2);
            Aggregates.update(entry2, value2);

            long entry3;
            if (length3 <= VECTOR_SIZE) {
                long result;
                // IDE-inlined Aggregates.putShort()
                long address = aggregates.pointer + hash3;
                ByteVector other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                if (vector3.compare(VectorOperators.EQ, other).allTrue()) {
                    result = address;
                } else {
                    for (long offset = hash3; ; offset = Aggregates.next(offset)) {
                        address = aggregates.pointer + offset;
                        other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                        if (vector3.compare(VectorOperators.EQ, other).allTrue()) {
                            result = address;
                            break;
                        }

                        if (UNSAFE.getInt(address) == 0) {
                            Aggregates.alloc(address, position3, length3, hash3);
                            result = address;
                            break;
                        }
                    }
                }

                entry3 = result;
            } else {
                length3 = findLong(position3);
                entry3 = aggregates.putLong(position3, length3, hash3);
            }

            // no vectorization, so keep them close to the branch
            position3 += length3;
            long value3 = UNSAFE.getLong(position3);
            long dot3 = Long.numberOfTrailingZeros(~value3 & DOT_BITS);
            position3 += (dot3 >> 3) + 3;
            value3 = value(value3, dot3);
            Aggregates.update(entry3, value3);

            long entry4;
            if (length4 <= VECTOR_SIZE) {
                long result;
                // IDE-inlined Aggregates.putShort()
                long address = aggregates.pointer + hash4;
                ByteVector other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                if (vector4.compare(VectorOperators.EQ, other).allTrue()) {
                    result = address;
                } else {
                    for (long offset = hash4; ; offset = Aggregates.next(offset)) {
                        address = aggregates.pointer + offset;
                        other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                        if (vector4.compare(VectorOperators.EQ, other).allTrue()) {
                            result = address;
                            break;
                        }

                        if (UNSAFE.getInt(address) == 0) {
                            Aggregates.alloc(address, position4, length4, hash4);
                            result = address;
                            break;
                        }
                    }
                }

                entry4 = result;
            } else {
                length4 = findLong(position4);
                entry4 = aggregates.putLong(position4, length4, hash4);
            }

            position4 += length4;
            long value4 = UNSAFE.getLong(position4);
            long dot4 = Long.numberOfTrailingZeros(~value4 & DOT_BITS);
            position4 += (dot4 >> 3) + 3;
            value4 = value(value4, dot4);
            Aggregates.update(entry4, value4);
        }

        loop1(aggregates, position1, limit1);
        loop1(aggregates, position2, limit2);
        loop1(aggregates, position3, limit3);
        loop1(aggregates, position4, limit4);
    }

    private static void loop1(Aggregates aggregates, long position, long limit) {
        while (position < limit) {
            ByteVector vector = ByteVector.fromMemorySegment(BV256, MEMORY, position, BO);
            int length = vector.eq(SEMICOLON).firstTrue() + 1;
            ByteVector mask = ByteVector.fromArray(BV256, MASKS, 33 - length);
            vector = vector.and(mask);

            long hash = Aggregates.hash(vector.reinterpretAsLongs().lane(0) ^ vector.reinterpretAsLongs().lane(1));
            long entry;
            if (length <= VECTOR_SIZE) {
                long result;
                // IDE-inlined Aggregates.putShort()
                long address = aggregates.pointer + hash;
                ByteVector other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                if (vector.compare(VectorOperators.EQ, other).allTrue()) {
                    result = address;
                } else {
                    for (long offset = hash; ; offset = Aggregates.next(offset)) {
                        address = aggregates.pointer + offset;
                        other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                        if (vector.compare(VectorOperators.EQ, other).allTrue()) {
                            result = address;
                            break;
                        }

                        if (UNSAFE.getInt(address) == 0) {
                            Aggregates.alloc(address, position, length, hash);
                            result = address;
                            break;
                        }
                    }
                }

                entry = result;
            } else {
                length = findLong(position);
                entry = aggregates.putLong(position, length, hash);
            }

            position += length;
            long value = UNSAFE.getLong(position);
            long dot = Long.numberOfTrailingZeros(~value & DOT_BITS);
            value = value(value, dot);
            position += (dot >> 3) + 3;

            Aggregates.update(entry, value);
        }
    }

    private static int findLong(long position) {
        int length = VECTOR_SIZE;

        for (int i = 0; i < 3; i++, length += VECTOR_SIZE) {
            ByteVector vector = ByteVector.fromMemorySegment(BV256, MEMORY, position + length, BO);
            long commas = vector.compare(VectorOperators.EQ, ';').toLong();

            if (commas != 0) {
                length += Long.numberOfTrailingZeros(commas) + 1;
                break;
            }
        }

        return length;
    }

    static long value(long word, long dot) {
        long signed = (~word << 59) >> 63;
        long mask = ~(signed & 0xFF);
        long digits = ((word & mask) << (28 - dot)) & 0x0F000F0F00L;
        long abs = ((digits * MAGIC_MULTIPLIER) >>> 32) & 0x3FF;
        return (abs ^ signed) - signed;
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

                loop(aggregates, position, limit);
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

            // from jvm sources: https://github.com/openjdk/jdk/blob/master/src/hotspot/share/utilities/copy.cpp#L213
            // it tries to set memory atomically with long if the address is aligned by 8
            // workaround to call memset
            UNSAFE.putByte(pointer, (byte) 0);
            UNSAFE.setMemory(pointer + 1, SIZE - 1, (byte) 0); // ~2 ms win
        }

        static long hash(long x) {
            long h = x * -7046029254386353131L;
            h ^= h >>> 35;
            return h & MASK;
        }

        static long putShort(Aggregates aggregates, ByteVector vector, long position, int length, long hash) {
            // IDE-inlined Aggregates.putShort()
            long address = aggregates.pointer + hash;
            ByteVector other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

            if (vector.compare(VectorOperators.EQ, other).allTrue()) {
                return address;
            }

            for (long offset = hash; ; offset = next(offset)) {
                address = aggregates.pointer + offset;
                other = ByteVector.fromMemorySegment(BV256, MEMORY, address + 24, BO);

                if (vector.compare(VectorOperators.EQ, other).allTrue()) {
                    return address;
                }

                if (UNSAFE.getInt(address) == 0) {
                    alloc(address, position, length, hash);
                    return address;
                }
            }
        }

        long putLong(long ptr, long length, long hash) {
            for (long offset = hash; ; offset = next(offset)) {
                long address = pointer + offset;
                if (equal(address + 24, ptr, length)) {
                    return address;
                }

                int len = UNSAFE.getInt(address);
                if (len == 0) {
                    alloc(address, ptr, length, hash);
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

                for (long offset = hash; ; offset = next(offset)) {
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

        static long next(long prev) {
            return (prev + 128) & (SIZE - 1);
        }

        static boolean equal(long leftAddress, long rightAddress, long length) {
            for (; length >= 8; length -= 8, leftAddress += 8, rightAddress += 8) {
                long left = UNSAFE.getLong(leftAddress);
                long right = UNSAFE.getLong(rightAddress);

                if (left != right) {
                    return false;
                }
            }

            for (; length > 0; length--, leftAddress++, rightAddress++) {
                byte left = UNSAFE.getByte(leftAddress);
                byte right = UNSAFE.getByte(rightAddress);

                if (left != right) {
                    return false;
                }
            }

            return true;
        }
    }
}