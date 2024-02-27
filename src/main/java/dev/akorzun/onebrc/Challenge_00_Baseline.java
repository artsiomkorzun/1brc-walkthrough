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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static dev.akorzun.onebrc.Challenge.round;
import static java.util.stream.Collectors.groupingBy;


public class Challenge_00_Baseline implements Challenge {

    public static void main(String[] args) {
        new Challenge_00_Baseline().run(args);
    }

    @Override
    public void solve(String[] args, Path file, PrintStream output) throws Exception {
        Function<String, Measurement> parser = (line) -> {    // allocates one string for line
            int index = line.indexOf(';');
            String station = line.substring(0, index);        // allocates one string
            double temperature = Double.parseDouble(line.substring(index + 1)); // allocates one string
            return new Measurement(station, temperature);     // allocates one measurement
        };

        Supplier<Aggregate> supplier = Aggregate::new;

        BiConsumer<Aggregate, Measurement> accumulator = (left, right) -> {
            left.min = Math.min(left.min, right.temperature);
            left.max = Math.max(left.max, right.temperature);
            left.sum += right.temperature;
            left.cnt++;
        };

        BinaryOperator<Aggregate> combiner = (left, right) -> {
            throw new IllegalStateException("Not used with 1 thread");
        };

        TreeMap<String, Aggregate> result = Files.lines(file) // sequential, uses 1 thread
                .map(parser)
                .collect(groupingBy(Measurement::station, TreeMap::new, Collector.of(supplier, accumulator, combiner)));

        output.println(result);
    }

    record Measurement(String station, double temperature) {
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
