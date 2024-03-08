package dev.akorzun.util;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class TimeRecorder {

    private final String title;
    private final long[] times = new long[1024];
    private final AtomicInteger size = new AtomicInteger();

    public TimeRecorder() {
        this("no-title");
    }

    public TimeRecorder(String title) {
        this.title = title;
    }

    public void record() {
        long time = System.currentTimeMillis();
        int index = size.getAndIncrement();
        times[index] = time;
    }

    public void print() {
        int count = this.size.get();
        Arrays.sort(times, 0, count);

        long first = times[0];
        long prev = first;

        System.out.println();
        System.out.println(title);
        System.out.println("| Place |   Time (ms)   | Diff First |  Diff Prev |");
        System.out.println("| +++++ | ++++++++++++++| ++++++++++ |  +++++++++ |");

        for (int i = 0; i < count; i++) {
            System.out.printf("| %5s | %13s | %10s | %10s |%n", i + 1, times[i], diff(i, first), diff(i, prev));
            prev = times[i];
        }

        System.out.println();
    }

    private String diff(int i, long first) {
        long diff = times[i] - first;
        return (diff == 0 ? "" : "+") + diff ;
    }

    public static void main(String[] args) throws InterruptedException {
        TimeRecorder recorder = new TimeRecorder();

        for (int i = 0; i < 10; i++) {
            recorder.record();
            Thread.sleep(1);
        }

        recorder.print();
    }

}
