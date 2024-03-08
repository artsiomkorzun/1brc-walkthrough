package dev.akorzun.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GCStats {
    public static void main(String[] args) throws IOException {
        Path file = Path.of("gc/01.txt");
        List<String> strings = Files.readAllLines(file);

        int lines = 0;
        int count = 0;
        double total = 0;

        for (String line : strings) {
            if (lines++ == 0) {
                continue;
            }

            if (!line.endsWith("ms")) {
                throw new IllegalArgumentException("Line #" + lines + ": " + line);
            }

            int index = line.lastIndexOf(' ');
            String text = line.substring(index, line.length() - 2);

            double number = Double.parseDouble(text);
            total += number;
            count++;
        }

        System.out.printf("GC stats: time=%.3f, count=%d", total, count);
    }
}
