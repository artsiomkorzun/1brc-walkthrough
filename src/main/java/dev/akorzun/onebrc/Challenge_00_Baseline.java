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
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collector;

import static dev.akorzun.onebrc.Challenge.round;
import static java.util.stream.Collectors.groupingBy;


public class Challenge_00_Baseline implements Challenge {

    public static void main(String[] args) {
        new Challenge_00_Baseline().run(args);
    }

    @Override
    public void solve(String[] args, Path file, PrintStream output) throws Exception {
        Map<String, Aggregate> result = Files.lines(file)             // sequential, uses 1 thread
                .map((line) -> {
                    String[] parts = line.split(";");           // look inside takes fast path, not regex
                    String station = parts[0];
                    double temperature = Double.parseDouble(parts[1]);
                    return new Measurement(station, temperature);
                })
                .collect(groupingBy(Measurement::station, Collector.of(
                        Aggregate::new,
                        (aggregate, measurement) -> {
                            aggregate.min = Math.min(aggregate.min, measurement.temperature);
                            aggregate.max = Math.max(aggregate.max, measurement.temperature);
                            aggregate.sum += measurement.temperature;
                            aggregate.cnt++;
                        },
                        (aggregate, aggregate2) -> {
                            throw new IllegalStateException("No merge with 1 thread");
                        }
                )));

        output.println(new TreeMap<>(result));
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
