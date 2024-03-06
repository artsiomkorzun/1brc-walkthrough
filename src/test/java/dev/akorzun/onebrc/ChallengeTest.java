package dev.akorzun.onebrc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

class ChallengeTest {

    @ParameterizedTest
    @MethodSource("samples")
    void testSamples(Params params) throws Exception {
        Challenge implementation = params.implementation;
        Path input = params.input;
        Path output = params.output;

        String expected = Files.readString(output, StandardCharsets.UTF_8);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        implementation.solve(new String[0], input, new PrintStream(stream));

        String actual = stream.toString(StandardCharsets.UTF_8);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testRandom(@TempDir Path temp) throws Exception {
        Random random = new Random();
        String[] stations = generate();

        Path input = temp.resolve("input.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(input, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 500000; i++) {
                String station = stations[random.nextInt(0, stations.length)];
                double temperature = random.nextInt(-999, 1000) / 10.0;
                writer.write(station);
                writer.write(';');
                writer.write(Double.toString(temperature));
                writer.write('\n');
            }
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        new Challenge_00_Baseline().solve(new String[0], input, new PrintStream(stream));
        String expected = stream.toString(StandardCharsets.UTF_8);

        for (Challenge implementation : implementations()) {
            stream = new ByteArrayOutputStream();
            implementation.solve(new String[0], input, new PrintStream(stream));

            String actual = stream.toString(StandardCharsets.UTF_8);
            Assertions.assertEquals(expected, actual, "Implementation: " + implementation.getClass().getSimpleName());
        }
    }

    static String[] generate() {
        Random random = new Random();
        String[] stations = new String[10000];

        for (int i = 0; i < stations.length; i++) {
            byte[] station = new byte[random.nextInt(1, 101)];

            for (int j = 0; j < station.length; j++) {
                byte b = (byte) random.nextInt(0, 128);
                boolean replace = switch (b) {
                    case ';', '\n' -> true;
                    default -> false;
                };

                if (replace) {
                    station[j] = '0';
                }
            }

            stations[i] = new String(station);
        }

        return stations;
    }

    static Collection<Params> samples() throws Exception {
        List<Params> params = new ArrayList<>();

        for (Challenge implementation : implementations()) {
            for (Path input : inputs()) {
                String name = input.getFileName().toString().replace(".txt", ".out");
                Path output = input.resolveSibling(name);
                params.add(new Params(implementation, input, output));
            }
        }

        return params;
    }

    static List<Path> inputs() throws Exception {
        try (Stream<Path> stream = Files.list(Path.of("src/test/resources/samples"))) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".txt")).toList();
        }
    }

    static Collection<Challenge> implementations() throws Exception {
        try (Stream<Path> stream = Files.list(Path.of("src/main/java/dev/akorzun/onebrc"))) {
            return stream.map(Path::getFileName)
                    .map(Objects::toString)
                    .filter(name -> name.startsWith("Challenge_") && name.endsWith(".java"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .map(name -> {
                        try {
                            Class<?> type = Class.forName("dev.akorzun.onebrc." + name);
                            Constructor<?> constructor = type.getConstructor();
                            return (Challenge) constructor.newInstance();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        }
    }

    record Params(Challenge implementation, Path input, Path output) {
        @Override
        public String toString() {
            String name = implementation.getClass().getSimpleName();
            return name.substring(name.lastIndexOf('_') + 1) + ": " + input.getFileName();
        }
    }
}
