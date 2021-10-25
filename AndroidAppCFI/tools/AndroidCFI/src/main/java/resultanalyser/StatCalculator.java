package resultanalyser;

import util.PathManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StatCalculator {

    public static void calculateScoresForWindowCountBased(Map<Integer, Map<Integer, Integer>> boundaries, Map<Integer, Map<Integer, Integer>> timings,
                                                          List<JavaMethod> analysedMethods, int threshold) {

        Map<Integer, String> revMIdMapping = analysedMethods.stream().filter(JavaMethod::isActive).collect(Collectors.toMap(JavaMethod::getLogParserId, JavaMethod::getShortName));

        System.out.println(IntStream.range(0, 79).mapToObj(i -> "-").collect(Collectors.joining()));

        System.out.format("%8s %8s %8s %8s %8s %8s %4s\n", "method_id|", "address_id|", "ground_truth_count|"
                , "hit_count|", "precision|", "recall|", "f1");
        System.out.println(IntStream.range(0, 79).mapToObj(i -> "-").collect(Collectors.joining()));

//        System.out.println("method_id|address_id|ground_truth_count|hit_count|precision|recall|f1".replace("|","\t"));

        for (int m = 0; m < boundaries.size(); m++) {
                List<Integer> scanTimings = timings.get(m).keySet().stream()
//                        .filter(k -> {
//                                    int timing = timings.get(finalScAdId).get(k);
//                                    return timing > 5 && timing < threshold;
//                                }
//                        )
                        .sorted().collect(Collectors.toList());
//                if (scanTimings.isEmpty()) {
//                    formatAndWriteToFile(revMIdMapping.get(m), scAdId, boundaryStartTimings.size(), 0, 0, 0, 0);
//                    continue;
//                }
                long truePositive = 0;
                long falsePositive = 0;
                int trueNegative = 0;
                long falseNegative = 0;

                List<Map.Entry<Integer, Integer>> entrySet = new ArrayList<>(boundaries.get(m).entrySet());


                truePositive = scanTimings.stream().filter(st -> entrySet.stream().anyMatch(es -> st >= es.getKey() && st <= es.getValue())).count();
                falsePositive = scanTimings.size()-truePositive;

                falseNegative = entrySet.stream().filter(es-> scanTimings.stream().noneMatch(st-> st >= es.getKey() && st <= es.getValue())).count();


//                if (scanTimings.stream().anyMatch(st -> entrySet.stream().anyMatch(es -> st >= es.getKey() && st <= es.getValue()))) {
//                    truePositive++;
//                }else {
//                    falsePositive++;
//                }
//
//
//
//
//
//                for (int mTime = 0; mTime < boundaryStartTimings.size(); mTime++) {
//                    int finalMTime = mTime;
//                    if (scanTimings.stream().anyMatch(i -> i > boundaryStartTimings.get(finalMTime) - 1200 && i < boundaryEndTimings.get(finalMTime) + 1200)) {
//                        truePositive++;
//                    }
//                }
//
//                List<Integer> mergedTimings = new ArrayList<>();
//                mergedTimings.add(scanTimings.get(0));
//                Integer mergeListTop = scanTimings.get(0);
//                for (Integer scanTime : scanTimings) {
//                    if (scanTime - mergeListTop > 200) {
//                        mergedTimings.add(scanTime);
//                        mergeListTop = scanTime;
//                    }
//                }
//                int falsePositive = mergedTimings.size() > truePositive ? mergedTimings.size() - truePositive : 0;
//                int falseNegative = boundaryStartTimings.size() > mergedTimings.size() ? boundaryStartTimings.size() - mergedTimings.size() : 0;

                double precision = getPrecision(truePositive, falsePositive);
                double recall = getRecall(truePositive, falseNegative);
                double f1Score = getF1(precision, recall);

                formatAndWriteToFile(m, revMIdMapping.get(m), entrySet.size(), scanTimings.size(), precision, recall, f1Score);

        }
        try {
            Files.write(Paths.get(PathManager.getAddressStatsOut()), "===========\n".getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static double getPrecision(long truePositive, long falsePositive) {
        return (truePositive * 1.0) / (truePositive + falsePositive);
    }

    static double getRecall(long truePositive, long falseNegative) {
        return (truePositive * 1.0) / (truePositive + falseNegative);
    }

    static double getF1(double precision, double recall) {
        return 2.0 / ((1 / recall) + (1 / precision));

    }

    private static void formatAndWriteToFile(int mId, String mName, int boundaryStartTimingsSize, int mergedTimingsSize, double precision, double recall, double f1Score) {
        String result = mId + "," + mName + "," + boundaryStartTimingsSize + "," + mergedTimingsSize + ","
                + String.format("%.2f", precision) + "," + String.format("%.2f", recall) + "," + String.format("%.2f", f1Score) + "\n";
        System.out.format("%8d %8s %22d %8d %8.2f %10.2f %6.2f\n", mId, mName, boundaryStartTimingsSize, mergedTimingsSize,
                precision, recall, f1Score);
        System.out.println(IntStream.range(0, 79).mapToObj(i -> "-").collect(Collectors.joining()));
        try {

            Files.write(Paths.get(PathManager.getAddressStatsOut()), result.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
