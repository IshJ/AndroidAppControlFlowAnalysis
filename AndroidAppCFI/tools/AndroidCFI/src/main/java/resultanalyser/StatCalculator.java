package resultanalyser;

import util.PathManager;
import util.PrettyTablePrinter;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static resultanalyser.ResultAnalyser.*;

public class StatCalculator {

    public static void calculateScoresForWindowCountBased(Map<Integer, Map<Integer, Integer>> boundaries, Map<Integer, Map<Integer, Integer>> timings,
                                                          List<JavaMethod> analysedMethods, int threshold, Map<Integer, Integer> timingToBoundary) {

        Map<Integer, String> revMIdMapping = analysedMethods.stream().filter(JavaMethod::isActive).collect(Collectors.toMap(JavaMethod::getLogParserId, JavaMethod::getShortName));
        Map<Integer, Integer> durationMap = new HashMap<>();
        Map<Integer, Integer> sizeMap = analysedMethods.stream().filter(JavaMethod::isActive).collect(Collectors.toMap(JavaMethod::getLogParserId, JavaMethod::getSize));
        BigInteger minOffset = analysedMethods.stream().map(m->m.getOdexOffsets().get(0))
                .map(o->new BigInteger(o, 16)).min(BigInteger::compareTo).get();
        Map<Integer, BigInteger> distanceMap =analysedMethods.stream().filter(JavaMethod::isActive).collect(Collectors.toMap(JavaMethod::getLogParserId,
                m-> new BigInteger(m.getOdexOffsets().get(0), 16).subtract(minOffset))) ;

        for (Integer mId : boundaries.keySet()) {
            int medianDuration = 0;
            if (boundaries.get(mId).size() > 1) {
                medianDuration = boundaries.get(mId).entrySet().stream()
                        .map(e -> e.getValue() - e.getKey()).sorted().collect(Collectors.toList())
                        .get(boundaries.get(mId).size() / 2 - 1);
            } else {
                medianDuration = boundaries.get(mId).keySet().stream().findFirst().get();
            }
            durationMap.put(mId,medianDuration);
        }

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
            long truePositive1 = 0;
            long falsePositive = 0;
            int trueNegative = 0;
            long falseNegative = 0;

            List<Map.Entry<Integer, Integer>> entrySet = new ArrayList<>(boundaries.get(timingToBoundary.get(m)).entrySet());


            truePositive = entrySet.stream().filter(es-> scanTimings.stream().anyMatch(st-> st >= es.getKey() && st <= es.getValue())).count();
            truePositive1 = scanTimings.stream().filter(st -> entrySet.stream().anyMatch(es -> st >= es.getKey() && st <= es.getValue())).count();
            falsePositive = scanTimings.size()> truePositive?scanTimings.size()- truePositive:0;

            falseNegative = entrySet.stream().filter(es -> scanTimings.stream().noneMatch(st -> st >= es.getKey() && st <= es.getValue())).count();

            double precision = getPrecision(truePositive, falsePositive);
            double recall = getRecall(truePositive, falseNegative);
            double f1Score = getF1(precision, recall);

            tableRows.add(new StringJoiner(columnSeparator)
                    .add(String.valueOf(m))
                    .add(revMIdMapping.get(m))
                    .add(String.valueOf(sizeMap.get(m)))
                    .add(distanceMap.get(m).toString(10))
                    .add(String.valueOf(durationMap.get(m)))
                    .add(String.valueOf(entrySet.size()))
                    .add(String.valueOf(scanTimings.size()))
                    .add(String.format("%.2f", precision))
                    .add(String.format("%.2f", recall))
                    .add(String.format("%.2f", f1Score)).toString()
            );
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

    public static List<String> getFormattedTable(List<String> rows) {
        List<String> headers = Arrays.asList("id", "name","width","distance","duration estimate", "ground truths"
                , "hits", "precision", "recall", "f1");
        return PrettyTablePrinter.printPrettyTable(headers, rows, columnSeparator);

    }
}
