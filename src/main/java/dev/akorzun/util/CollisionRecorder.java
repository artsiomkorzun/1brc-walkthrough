package dev.akorzun.util;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class CollisionRecorder {

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

    private final Set<String> collisions = new HashSet<>();

    public void record(long index, long position, long length) {
        byte[] array = new byte[(int) length];
        UNSAFE.copyMemory(null, position, array, Unsafe.ARRAY_BYTE_BASE_OFFSET, length);
        String key = new String(array, StandardCharsets.UTF_8);
        collisions.add(key);
    }

    public void print() {
        System.err.println("Thread: " + Thread.currentThread() + ". Collisions: " + collisions.size());
    }
}
