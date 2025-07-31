package dev.akorzun.onebrc;

import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

@Fork(1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
public class VectorBenchmark {

    private static final long DOT_BITS = 0x10101000;
    private static final long MAGIC_MULTIPLIER = (100 * 0x1000000 + 10 * 0x10000 + 1);
    private static final byte SEMICOLON = ';';
    private static final Unsafe U;

    private long a = 1;
    private long b = 2;
    private long c = 3;
    private long d = 4;

    private long[] arrays = new long[4];

    static {
        try {
            Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
            unsafe.setAccessible(true);
            U = (Unsafe) unsafe.get(Unsafe.class);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static final ByteOrder BO = ByteOrder.nativeOrder();
    private static final VectorSpecies<Byte> BV256 = ByteVector.SPECIES_256;
    private static final VectorSpecies<Long> LV256 = LongVector.SPECIES_256;

    private byte[] bytes = new byte[128];
    private long[] longs = new long[4];
    private boolean[] bools = new boolean[4];

    long l3;
    long l1;
    long l4;
    long l2;

    {
        for (int i = 0; i < 16; i++) {
            if(i % 4 == 0) {
                bytes[i] = 64;
            }
        }
    }

    @Setup
    public void setup() {
    }

    //@Benchmark
    public int initMaskFromValues() {
        boolean l1 = bools[1];
        boolean l2 = bools[0];
        boolean l3 = bools[3];
        boolean l4 = bools[2];

        return VectorMask.fromValues(LV256, l1, l2, l3, l4).firstTrue(); // creates array then loads
    }

    //@Benchmark
    public int initVectorFromLane() {
        return LongVector.zero(LV256)
                .withLane(0, l1)
                .withLane(1, l2)
                .withLane(2, l3)
                .withLane(3, l4)
                .reinterpretAsInts()
                .lane(0);
    }

    //@Benchmark
    public int initVectorFromArray() {
        long[] longs = new long[4];
        longs[0] = l1;
        longs[1] = l2;
        longs[2] = l3;
        longs[3] = l4;

        return LongVector.fromArray(LV256, longs, 0)
                .lanewise(VectorOperators.TRAILING_ZEROS_COUNT)
                .reinterpretAsInts()
                .lane(0);
    }

    /*@Benchmark
    public int merryKitty() {
        LongVector nums = LongVector.fromArray(LV256, longs, 0);
        LongVector notNums = nums.not();
        LongVector dots = notNums.and(DOT_BITS).lanewise(VectorOperators.TRAILING_ZEROS_COUNT);
        LongVector signs = notNums.lanewise(VectorOperators.LSHL, 59).lanewise(VectorOperators.ASHR, 63);
        LongVector masks = signs.and(0xFF).not();

        nums = nums.and(masks).lanewise(VectorOperators.LSHL, LongVector.broadcast(LV256, 28)
                .sub(dots))
                .and(0x0F000F0F00L)
                .mul(MAGIC_MULTIPLIER)
                .lanewise(VectorOperators.LSHR, 32)
                .and(0x3FF)
                .lanewise(VectorOperators.XOR, signs)
                .sub(signs);

        dots = dots.lanewise(VectorOperators.LSHR, 3).add(3);

        return (int) (nums.lane(0) + dots.lane(1));
    }*/

    @Benchmark
    @CompilerControl(CompilerControl.Mode.PRINT)
    public long valueVectorWithLanes() {
        ByteVector vector = LongVector.zero(LongVector.SPECIES_256)
                .withLane(2, a)
                .withLane(0, a)
                .withLane(1, b)
                .withLane(2, c)
                .withLane(3, d)
                .reinterpretAsBytes();

        LongVector minuses = vector.compare(VectorOperators.EQ, '-')
                .toVector()
                .reinterpretAsLongs()
                .and(0x1);

        //   X.XX or X.X (needs left shift 8)
        LongVector numbers = vector.reinterpretAsLongs().lanewise(VectorOperators.LSHR, minuses.lanewise(VectorOperators.LSHL, 3));
        LongVector lines = numbers.reinterpretAsBytes().compare(VectorOperators.EQ, '\n')
                .toVector().reinterpretAsLongs()
                .lanewise(VectorOperators.LSHR, 24)
                .and(0x1);

        numbers = numbers.lanewise(VectorOperators.LSHL, lines.lanewise(VectorOperators.LSHL, 3));


        // XY => 100 * Y + 10 * X
        IntVector integers = numbers.reinterpretAsInts().and(0x0F000F0F).mul(100 * 0x10000 + 10 * 0x100).lanewise(VectorOperators.LSHR, 16);
        IntVector fractions = numbers.reinterpretAsInts().lanewise(VectorOperators.LSHR, 24).and(0x0F);
        numbers = integers.add(fractions).reinterpretAsLongs().and(0x03FF);

        LongVector signs = minuses.neg();
        numbers = numbers.lanewise(VectorOperators.XOR, signs).sub(signs);

        LongVector lengths = LongVector.broadcast(LongVector.SPECIES_256, 5).add(minuses).sub(lines);

        return numbers.lane(0) + numbers.lane(1) + numbers.lane(2) + numbers.lane(3)
                + lengths.lane(0) + lengths.lane(1) + lengths.lane(2) + lengths.lane(3);
    }

    //@Benchmark
    public long merrykitty() {
        return value(a) + value(b) + value(c) + value(d);
    }

    static long value(long word) {
        long dot = Long.numberOfTrailingZeros(~word & DOT_BITS);
        long signed = (~word << 59) >> 63;
        long mask = ~(signed & 0xFF);
        long digits = ((word & mask) << (28 - dot)) & 0x0F000F0F00L;
        long abs = ((digits * MAGIC_MULTIPLIER) >>> 32) & 0x3FF;
        return (abs ^ signed) - signed + (dot >> 3) + 3;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(VectorBenchmark.class.getSimpleName())
               // .addProfiler(LinuxPerfAsmProfiler.class)
                .jvmArgsAppend("--enable-preview", "--add-modules", "jdk.incubator.vector", "-Dsun.misc.unsafe.memory.access=allow", "-Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0")
                .build();

        new Runner(opt).run();
    }
}

