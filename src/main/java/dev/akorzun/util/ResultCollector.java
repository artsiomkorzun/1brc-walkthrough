package dev.akorzun.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

class ResultCollector {

    static final String MEAN = "\"mean\": ";
    static final String ERROR = "\"stddev\": ";

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Map<String, String> names = names();
        Set<Path> files = Files.list(directory).collect(Collectors.toCollection(TreeSet::new));
        double prev = Double.NaN;

        for (Path file : files) {
            String number = file.getFileName().toString();
            number = number.substring(0, number.length() - ".json".length());
            String name = names.getOrDefault(number, "n/a");

            String json = Files.readString(file);
            int meanFrom = json.indexOf(MEAN) + MEAN.length();
            int meanTo = json.indexOf(',', meanFrom);
            int errorFrom = json.indexOf(ERROR) + ERROR.length();
            int errorTo = json.indexOf(',', errorFrom);

            double mean = Double.parseDouble(json.substring(meanFrom, meanTo));
            double error = Double.parseDouble(json.substring(errorFrom, errorTo));

            System.err.printf("|%s|   %-25s| %8.3f ± %.3f| %7.2f|%n",
                    number, name,
                    mean, error, mean / prev * 100 - 100);

            prev = mean;
        }
    }

    static Map<String, String> names() throws Exception {
        Map<String,String> map = new HashMap<>();
        Files.list(Path.of("src/main/java/dev/akorzun/onebrc"))
                .map(Path::getFileName)
                .map(Objects::toString)
                .filter(name -> name.startsWith("Challenge_") && name.endsWith(".java"))
                .map(name -> name.substring("Challenge_".length(), name.length() - ".java".length()))
                .forEach(name -> {
                    String key = name.substring(0, "00".length());
                    String value = name.substring("00_".length());
                    map.put(key, value);
                });
        return map;
    }

}
