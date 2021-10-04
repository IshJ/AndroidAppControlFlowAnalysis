package resultanalyser;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import util.ConfigManager;
import util.PathManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ResultAnalyser {
    static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    static String title = "aspect-based";

    static boolean isFilteringAddresses = false;
    static List<Integer> filterAddressIds = Arrays.asList(0);

    static boolean isFilteringMethods = false;
    static List<Integer> filterMethodIds = Arrays.asList(2);

    static boolean isFilteringByFrequency = false;
    static boolean allAddressesInOneGraph = true;
    static int splitCount = 1;
    private static final boolean onlyCalcScores = false;
    private static final boolean combineGraphs = false;

    private static final boolean heapIssue = false;
    static int recordSplitCount = 4;
    static int recordSplitCountStart = 0;

    static boolean aroundGroundTruths = false;
    static boolean hitCounterBased = false;

    static boolean plotForAllTimes = false;

    static boolean onlyClusters = false;
    static boolean onlyClusterPoints = false;
    static final int windowSize = 60;
    static final int minPointsToCluster = 8;

    public static int threshold = 215;
    public static boolean onlyPositive = false;

    private static final String innerRecordSplitter = "\\|";

    private static final Color TRANSPARENT_COLOUR = new Color(0x00FFFFFF, true);

    private static final int concatIndex = 2000000;


    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                title = args[0];
            }
            List<String> sideChannelLines = Files.lines(Paths.get(PathManager.getSideChannelDbPath()), StandardCharsets.ISO_8859_1).collect(Collectors.toList());
            List<String> groundTruthLines = Files.lines(Paths.get(PathManager.getGroundTruthDbPath()), StandardCharsets.ISO_8859_1).collect(Collectors.toList());

            filterSideChannelRecords(sideChannelLines, groundTruthLines, aroundGroundTruths, hitCounterBased);

            List<String> logLines = Files.lines(Paths.get(PathManager.getLogPath()), StandardCharsets.ISO_8859_1).collect(Collectors.toList());
            List<String> filteredSideChannelLines = Files.lines(Paths.get(PathManager.getFilteredOutputPath()), StandardCharsets.ISO_8859_1).collect(Collectors.toList());

            Map<String, String> toolConfigMap = ConfigManager.readConfigs(PathManager.getToolConfigFilePath());
            Map<String, String> configMap = ConfigManager.readConfigs(PathManager.getConfigFilePath());

            title = title + "_" + (toolConfigMap.get("threshold") == null ? "" : toolConfigMap.get("threshold")) + "_";
            threshold = Integer.parseInt(toolConfigMap.get("threshold"));

            if ("0".equals(toolConfigMap.get("parseLogs"))) {
                return;
            }
            if (heapIssue) {
                filteredSideChannelLines = filteredSideChannelLines.subList(filteredSideChannelLines.size() * recordSplitCountStart / recordSplitCount,
                        filteredSideChannelLines.size() * (recordSplitCountStart + 1) / recordSplitCount);
                groundTruthLines = groundTruthLines.subList(groundTruthLines.size() * recordSplitCountStart / recordSplitCount,
                        groundTruthLines.size() * (recordSplitCountStart + 1) / recordSplitCount);
            }


            String baseAddress = getBaseAddress(logLines);
//            map containing the mapping from odex method ids to aop methodmap ids.
            Map<Integer, Integer> odexToAppMethodIdMap = resolveMethods(toolConfigMap);
//            32,33,2
            List<Integer> activeOdexM = new ArrayList<>(odexToAppMethodIdMap.keySet());

            List<String> seriesNames = getAddressIdNameMapping(toolConfigMap, activeOdexM);

            List<JavaMethod> analysedMethods = getAnalysedMethods(odexToAppMethodIdMap, toolConfigMap, configMap);

            String graphName = generateGraph(groundTruthLines, filteredSideChannelLines, seriesNames, title, analysedMethods);
            executePythonScript();
            backupData(graphName);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    private static void executePythonScript() throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec("python3 " + PathManager.getScriptFolderPath() + "heatMapGenerator.py");
        InputStream stdout = p.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("stdout: " + line);
        }

    }


    private static void backupData(String graphName) {
        long timeStamp = System.currentTimeMillis();
        File backupPath = new File(PathManager.getDbFolderPath() + timeStamp);
        String backupPathString = PathManager.getDbFolderPath() + timeStamp + File.separator;
        backupPath.mkdir();
        try {

            Files.copy(new File(PathManager.getSideChannelDbPath()).toPath(), new File(backupPathString + "side_channel_info_full.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getGroundTruthDbPath()).toPath(), new File(backupPathString + "ground_truth_full.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getProcessedRecordsPath()).toPath(), new File(backupPathString + "processedRecords.out").toPath(), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(new File(PathManager.getLogPath()).toPath(), new File(backupPathString + "log.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getOatFilePath()).toPath(), new File(backupPathString + "oatdump.out").toPath(), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(new File(PathManager.getConfigFilePath()).toPath(), new File(backupPathString + "config.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getToolConfigFilePath()).toPath(), new File(backupPathString + "toolConfig.out").toPath(), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(new File(PathManager.getGraphFolderPath() + "heatmap.png").toPath(), new File(backupPathString + "heatmap.png").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(graphName).toPath(), new File(backupPathString + "scatterPlot.png").toPath(), StandardCopyOption.REPLACE_EXISTING);


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    static List<JavaMethod> getAnalysedMethods(Map<Integer, Integer> odexToAppMethodIdMap, Map<String, String> toolConfigMap, Map<String, String> configMap) {
        List<JavaMethod> methods = new ArrayList<>();
        Map<Integer, String> methodIdAdMapping = Arrays.stream(toolConfigMap.get("methodIdAdMapping").split("\\|")).collect(Collectors.toMap(
                s -> Integer.parseInt(s.split("=")[0]), s -> s.split("=")[1]
        ));

        Map<String, Integer> revMethodIdAdMapping = methodIdAdMapping.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        Map<Integer, String> methodIdNameMapping = Arrays.stream(toolConfigMap.get("methodIdNameMapping").split("\\|")).collect(Collectors.toMap(
                s -> Integer.parseInt(s.split("=")[0].strip()), s -> s.split("=")[1].strip()
        ));
        Map<Integer, Integer> methodIdSizeMapping = Arrays.stream(toolConfigMap.get("methodIdSizeMapping").split("\\|")).collect(Collectors.toMap(
                s -> Integer.parseInt(s.split("=")[0]), s -> Integer.parseInt(s.split("=")[1])
        ));

        Map<Integer, String> appMethodIdNameMapping = Arrays.stream(toolConfigMap.get("MethodMap").split("\\|"))
                .filter(s -> s.contains("="))
                .collect(Collectors.toMap(
                s -> Integer.parseInt(s.split("=")[1].strip()), s -> s.split("=")[0].strip()
        ));

        List<String> odexOffsets = new ArrayList<>(List.of(configMap.get("sideChannelOffsets").split(",")));
        List<String> appOffsets = new ArrayList<>(List.of(toolConfigMap.get("offsets").split(",")));
        odexOffsets.remove(0);
        appOffsets.remove(0);

        int curId = 0;
        for (int i = 0; i < odexOffsets.size(); i++) {
//            7e67ej
            String odexOffset = odexOffsets.get(i);
            Integer odexId = revMethodIdAdMapping.get(odexOffset);
            String odexName = methodIdNameMapping.get(odexId);
            Integer odexSize = methodIdSizeMapping.get(odexId);

//            494287032072 (base ad+odexOffset)
            String appOffset = appOffsets.get(i);

//            from aspects
            Integer appId = odexToAppMethodIdMap.getOrDefault(odexId, -1);
            String appName = appMethodIdNameMapping.getOrDefault(appId, "");

            JavaMethod jMethod = new JavaMethod();
            jMethod.addOdexOffset(odexOffset);
            jMethod.addAppOffset(appOffset);
            jMethod.setOdexMethodId(odexId);
            jMethod.setAppMethodId(appId);
            jMethod.setOdexName(odexName);
            jMethod.setSize(odexSize);
            jMethod.setAppName(appName);
            jMethod.setActive(appId > -1);
            jMethod.setLogParserId(appId > -1 ? curId++ : -1);

            methods.add(jMethod);
        }

        return methods;
    }


    public static String generateGraph(List<String> groundTruthLines, List<String> sideChannelLines,
                                       List<String> seriesNames,
                                       String title, List<JavaMethod> analysedMethods) throws IOException {
        Map<Integer, Map<Integer, Integer>> boundaries = parseForBoundaries(groundTruthLines, true, analysedMethods);
        String graphName = "";
//        filterMethodIds = filterAddressIds.stream().map(methodIdMapping::get).collect(Collectors.toList());
        Map<Integer, Map<Integer, Integer>> timings = parseForAddress(sideChannelLines, true, analysedMethods);

        writeToFile(boundaries, timings);

        Map<Integer, List<Color>> colourMap = getColourLists(timings.size(), boundaries.size());

        List<Integer> timeKeys = timings.keySet().stream()
                .map(k -> new ArrayList<>(timings.get(k).keySet())).collect(Collectors.toList()).stream()
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        ;
        timeKeys.remove(timeKeys.size() - 1);
        List<JFreeChart> charts = new ArrayList<>();

        try {
            Map<Integer, Map<Integer, Integer>> tempTimings = new HashMap<>();
            Map<Integer, Map<Integer, Integer>> tempBoundaries = new HashMap<>();
            List<String> graphNames = new ArrayList<>();
            if (isFilteringMethods) {
                IntStream.range(0, filterMethodIds.size()).forEach(i -> tempBoundaries.put(i, new HashMap<>()));
            } else {
                IntStream.range(0, boundaries.size()).forEach(i -> tempBoundaries.put(i, new HashMap<>()));
            }

            if (allAddressesInOneGraph) {
                if (isFilteringAddresses) {
                    IntStream.range(0, filterAddressIds.size()).forEach(i -> tempTimings.put(i, new HashMap<>()));
                } else {
                    IntStream.range(0, timings.size()).forEach(i -> tempTimings.put(i, new HashMap<>()));

                }
            } else {
                tempTimings.put(0, new HashMap<>());
            }

            int graphSize = timeKeys.size() / splitCount;
            for (int i = 0; i < splitCount; i++) {
                List<Integer> keys = timeKeys.subList(i * graphSize, (i + 1) * graphSize);
                if (isFilteringMethods) {
                    IntStream.range(0, filterMethodIds.size()).forEach(bkey ->
                            keys.forEach(k -> tempBoundaries.get(bkey).put(k, boundaries.get(filterMethodIds.get(bkey)).get(k))));
                } else {
                    tempBoundaries.keySet().forEach(bkey -> boundaries.get(bkey).keySet()
                            .forEach(k -> tempBoundaries.get(bkey).put(k, boundaries.get(bkey).get(k))));
                }

                if (allAddressesInOneGraph) {
                    if (isFilteringAddresses) {
                        tempTimings.keySet().forEach(tkey -> keys.forEach(k -> tempTimings.get(tkey).put(k, timings.get(filterAddressIds.get(tkey)).get(k))));
                    } else {
                        tempTimings.keySet().forEach(tkey -> keys.forEach(k -> tempTimings.get(tkey).put(k, timings.get(tkey).get(k))));

                    }
                    graphName = PathManager.getGraphFolderPath() + title + i;
                    if (splitCount == 1 && !combineGraphs) {
                        graphName = PathManager.getGraphFolderPath() + title + i;
                    }

                    graphName = createCombGraph(tempTimings, tempBoundaries, seriesNames, colourMap, graphName, analysedMethods);
                    if (combineGraphs) {
                        graphNames.add(graphName);
                    }


                } else {
                    for (int ad = 0; ad < timings.size(); ad++) {
                        if (isFilteringAddresses && !filterAddressIds.contains(ad)) {
                            continue;
                        }
                        System.out.println("starting " + i + "_" + ad);
                        int finalAd = ad;
                        keys.forEach(k -> tempTimings.get(0).put(k, timings.get(finalAd).get(k)));
                        graphName = PathManager.getGraphFolderPath() + title + i + "_" + ad;
                        if (splitCount == 1 && !combineGraphs) {
                            graphName = PathManager.getGraphFolderPath() + title + i + "_" + ad;
                        }

                        graphName = createCombGraph(tempTimings, tempBoundaries, seriesNames, colourMap, graphName, analysedMethods);

                        if (combineGraphs) {
                            graphNames.add(graphName);
                        }
                        tempTimings.get(0).clear();
                    }
                }
                tempBoundaries.keySet().forEach(k -> tempBoundaries.get(k).clear());
                tempTimings.keySet().forEach(k -> tempTimings.get(k).clear());

            }
            if (combineGraphs) {
                graphName = combineGraphsHr(graphNames);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
        return graphName;
    }

    private static String createCombGraph(Map<Integer, Map<Integer, Integer>> tempTimings, Map<Integer, Map<Integer, Integer>> tempBoundaries,
                                          List<String> seriesNames, Map<Integer, List<Color>> colourMap, String graphName, List<JavaMethod> analysedMethods) throws IOException {
        JFreeChart chartGt = createCombinedMap(tempTimings, tempBoundaries, colourMap, analysedMethods);
        graphName = graphName + "_gt_" + System.currentTimeMillis() + ".png";
        ChartUtils.saveChartAsPNG(new File(graphName), chartGt, 5000, 800);
        return graphName;
    }


    static Map<Integer, Map<Integer, Integer>> parseForBoundaries(List<String> lines, boolean isGenerateFraph, List<JavaMethod> analysedMethods) {

        Map<Integer, Map<Integer, Integer>> boundaries = new HashMap<>();
        Map<Integer, Integer> appAddressToId = analysedMethods.stream().filter(JavaMethod::isActive).collect(Collectors.toMap(JavaMethod::getAppMethodId, JavaMethod::getLogParserId));
        appAddressToId.values().forEach(i -> boundaries.put(i, new HashMap<>()));
        int prevEnd = 0;
        for (String line : lines) {

            String[] splits = line.split(innerRecordSplitter);
            String methodInfo = splits[1];
            int timingCount = Integer.parseInt(splits[2]);
            int startTimingCount = Integer.parseInt(splits[1]);
            int endTimingCount = Integer.parseInt(splits[2]);
            int methodId = Integer.parseInt(splits[0]);
            if (startTimingCount == -1 || !appAddressToId.containsKey(methodId)) {
                continue;
            }
            methodId = appAddressToId.get(methodId);
            if (!boundaries.containsKey(methodId)) {
                boundaries.put(methodId, new HashMap<>());
            }

//          adjustment for overlapping
            if (prevEnd == startTimingCount) {
                startTimingCount += 2;
            }
            boundaries.get(methodId).put(startTimingCount, methodId);
        }

        System.out.println("done parsing boundaries");
        return boundaries;
    }

    //mid-> (time-> value)
    static Map<Integer, Map<Integer, Integer>> parseForAddress(List<String> lines, boolean isGenerateFraph, List<JavaMethod> analysedMethods) {

//        1623198431683|364|521462870688|198109
        Map<String, Integer> appAddressToId = analysedMethods.stream().filter(JavaMethod::isActive).collect(Collectors.toMap(m -> m.getAppOffsets().get(0), JavaMethod::getLogParserId));

        Map<Integer, Map<Integer, Integer>> timings = new HashMap<>();

        appAddressToId.values().forEach(i -> timings.put(i, new HashMap<>()));

        for (String line : lines) {
            String[] splits = line.split("\\|");
            Integer timingCount = Integer.parseInt(splits[3]);

            if (!appAddressToId.containsKey(splits[2])) {
                continue;
            }
            int methodId = appAddressToId.get(splits[2]);
            int timing = Integer.parseInt(splits[1]);

//          filtering for specific methods
            if (isFilteringAddresses && !filterAddressIds.contains(methodId)) {
                continue;
            }

            if (plotForAllTimes || timing < threshold) {
                timings.get(methodId).put(timingCount, methodId);
            }
        }
        Map<Integer, Long> freqFilterMap = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        timings.keySet().forEach(m -> freqFilterMap.put(m, timings.get(m).values().stream().count()));
        freqFilterMap.keySet().stream().map(m -> "address " + m + " -> " + freqFilterMap.get(m) + " hits\n").forEach(temp -> {
            System.out.print(temp);
            sb.append(temp);
        });
        sb.append("###\n");

//        filtering by frequency
        if (isFilteringByFrequency) {
            freqFilterMap.keySet().stream().filter(k -> freqFilterMap.get(k) > 1000).forEach(k -> timings.get(k).keySet().forEach(t -> timings.get(k).put(t, 1000)));
        }

        System.out.println("done parsing timings");
        return timings;
    }


    static Map<Integer, Integer> resolveMethods(Map<String, String> toolConfigMap) {
        Map<String, Integer> appAddressIdMapping = Arrays.stream(toolConfigMap.get("MethodMap").split("\\|"))
                .collect(Collectors.toMap(s -> s.split("=")[0].strip(), s -> Integer.parseInt(s.split("=")[1])));
        Map<String, Integer> odexAddressIdMapping = Arrays.stream(toolConfigMap.get("methodIdNameMapping").split("\\|"))
                .collect(Collectors.toMap(s -> s.split("=")[1].strip(), s -> Integer.parseInt(s.split("=")[0].strip())));
        Map<Integer, Integer> odexToApp = new HashMap<>();

        List<String> odexMethods = new ArrayList<>(odexAddressIdMapping.keySet());
        List<String> appMethods = new ArrayList<>(appAddressIdMapping.keySet());

        for (String appM : appMethods) {
            String matchingMethod = isMethodIncluded(appM, odexMethods);
            if (!matchingMethod.isEmpty()) {
                odexToApp.put(odexAddressIdMapping.get(matchingMethod), appAddressIdMapping.get(appM));
            }
        }

        System.out.println("mapped method count: " + odexToApp.size());
        return odexToApp;

    }

    private static String isMethodIncluded(String appM, List<String> m2) {

        return m2.stream().
                filter(m -> appM.split("\\(")[0].split(" ")[1].contains(m.split("\\(")[0].split(" ")[1]) && compareArgs(appM.split("\\(")[1].split("\\)"), m.split("\\(")[1].split("\\)"))).
                findAny().orElse("");
    }

    private static boolean compareArgs(String[] appM, String[] m2) {
        if (appM.length == 0) {
            if (m2.length == 0) {
                return true;
            }
            return false;
        }
        if (m2.length == 0) {
            return false;
        }

        String[] appMArgs = appM[0].replace("$", ".").split(",");
        String[] odexMArgs = m2[0].replace("$", ".").split(",");
        if (appMArgs.length != odexMArgs.length) {
            return false;
        }
        return !IntStream.range(0, odexMArgs.length).anyMatch(i -> !(appMArgs[i].contains(odexMArgs[i].strip()) || odexMArgs[i].contains(appMArgs[i].strip())));

    }

    //this method assumes all addresses in odex will be
    private static List<String> getAddressIdNameMapping(Map<String, String> toolConfigMap, List<Integer> activeOdexM) {
        if (!toolConfigMap.isEmpty()) {
            List<String> odexMethodMappings = Arrays.stream(toolConfigMap.get("methodIdAdMapping").split("\\|")).map(v -> v.split("=")[0]).collect(Collectors.toList());

            return activeOdexM.stream().map(odexMethodMappings::get).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }


    private static void filterSideChannelRecords(List<String> sideChannelLines, List<String> groundTruthLines
            , boolean aroundGroundTruths, boolean hitCounterBased) {
        List<String> filteredLines = new ArrayList<>();
        int groundTruthBegin = 0;
        int groundTruthEnd = Integer.parseInt(groundTruthLines.get(groundTruthLines.size() - 1).split(innerRecordSplitter)[2]);

        if (hitCounterBased) {

            Map<String, Integer> hitCounterMap = new HashMap<>();
            Map<String, Integer> pauseCounterMap = new HashMap<>();


            for (int i = 0; i < sideChannelLines.size(); i++) {
                String line = sideChannelLines.get(i);
                String[] splits = line.split(innerRecordSplitter);
                int timing = Integer.parseInt(splits[1]);

                String address = splits[2];
                if (!hitCounterMap.containsKey(address)) {
                    hitCounterMap.put(address, 0);
                    pauseCounterMap.put(address, 0);
                }
//                ###
                if (timing < threshold) {
                    if (pauseCounterMap.get(address) > 1000 || hitCounterMap.get(address) > 0) {
                        hitCounterMap.put(address, hitCounterMap.get(address) + 1);
                    }
                    pauseCounterMap.put(address, 0);
                } else {
                    pauseCounterMap.put(address, pauseCounterMap.get(address) + 1);
                    hitCounterMap.put(address, 0);
                }
//                ###
                if (hitCounterMap.get(address) > 0) {
                    if (!aroundGroundTruths || (timing > groundTruthBegin - 10 && timing < groundTruthEnd + 10)) {
                        filteredLines.add(line);
                    }
                }

            }
        } else {
            sideChannelLines.stream().filter(line -> {
                int timing = Integer.parseInt(line.split(innerRecordSplitter)[1]);
                return timing < threshold && (!aroundGroundTruths || (timing > groundTruthBegin - 10 && timing < groundTruthEnd + 10));
            }).forEach(filteredLines::add);
        }

        System.out.println("Number of records: " + filteredLines.size());
        try {
            Files.write(Path.of(PathManager.getFilteredOutputPath()), filteredLines, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    static String getBaseAddress(List<String> lines) {
        for (String line : lines) {
            if (line.contains("Odex x starting Address:")) {
                return line.split("Odex x starting Address:")[1].strip();
            }
        }
        return "";
    }

    static Map<String, Integer> getAddressIdMapping(String baseAddress, List<String> sideChannelLines, Map<String, String> toolConfigMap,
                                                    List<Integer> activeOdexM, Map<Integer, Integer> methodIdMapping) {

        List<String> offsets = getScannedOffsets(sideChannelLines, toolConfigMap);
//        494290026236 -> {Integer@1335} 14
        Map<String, Integer> mapping = IntStream.range(0, activeOdexM.size()).boxed().collect(Collectors.toMap(i -> offsets.get(activeOdexM.get(i)), i -> i));
        Map<String, Integer> tempMapping = activeOdexM.stream().collect(Collectors.toMap(offsets::get, i -> i));
//        IntStream.range(0, offsets.size()).forEach(i -> mapping.put(offsets.get(i), i));
        mapping.keySet().forEach(k -> methodIdMapping.put(mapping.get(k), tempMapping.get(k)));
        return mapping;

    }

    private static List<String> getScannedOffsets(List<String> sideChannelLines, Map<String, String> toolConfigMap) {

        if (!toolConfigMap.isEmpty()) {
            List<String> offsets = Arrays.asList(toolConfigMap.get("offsets").split(","));
//            first address is dummy
            return offsets.subList(1, offsets.size());
        }
        return sideChannelLines.stream().map(line -> line.split("\\|")[2]).distinct().sorted().collect(Collectors.toList());
    }


    private static void writeToFile(Map<Integer, Map<Integer, Integer>> boundaries, Map<Integer, Map<Integer, Integer>> timings) throws IOException {

        List<String> records = new ArrayList<>();
        List<Integer> keyList = new ArrayList<>(timings.keySet());

//        keyList.stream().filter(k -> timings.get(k).isEmpty() || boundaries.get(k).isEmpty()).forEach(k -> {
//            timings.remove(k);
//            boundaries.remove(k);
//        });
        keyList = new ArrayList<>(timings.keySet());
        for (int k = 0; k < keyList.size(); k++) {
            Integer mId = keyList.get(k);
            List<Integer> mTimingList = new ArrayList<>(new HashSet<>() {
                {
                    addAll(timings.get(mId).keySet());
                    addAll(boundaries.get(mId).keySet());
                }
            });
            mTimingList = mTimingList.stream().sorted().collect(Collectors.toList());
            int max = mTimingList.get(mTimingList.size() - 1) - concatIndex;

            for (int i = 0; i < max; i = i + concatIndex) {
                int finalI = i;
                int timingCount = (int) mTimingList.stream().filter(t -> t > finalI && t < finalI + concatIndex)
                        .filter(t -> timings.get(mId).containsKey(t)).count();
                int boundaryCount = (int) mTimingList.stream().filter(t -> t > finalI && t < finalI + concatIndex)
                        .filter(t -> boundaries.get(mId).containsKey(t)).count();
                records.add(formatToRecord(List.of(i / concatIndex, mId, timingCount, boundaryCount)));

            }

        }
        System.out.println("records size: " + records.size());
        Files.write(Path.of(PathManager.getProcessedRecordsPath()), records, StandardCharsets.ISO_8859_1);


    }

    private static String formatToRecord(List<Integer> recordElements) {
        StringJoiner sj = new StringJoiner(",");
        recordElements.stream().map(String::valueOf).forEach(sj::add);
        return sj.toString();
    }


    //  lines
    private static DefaultCategoryDataset createDatasetLines(Map<Integer, Map<Integer, Integer>> timings, List<String> seriesNames) {

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        int nX = timings.size();
        while (timings.size() > seriesNames.size()) {
            timings.remove(timings.keySet().size() - 1);
        }
        List<String> seriesList = IntStream.range(0, timings.size()).mapToObj(i -> "ad" + seriesNames.get(i) + "_" + i).collect(Collectors.toList());
//        for (int i = 0; i < nX; i++) {
//            seriesList.add("m"+seriesNames.get(i)+"_" + i);
//        }

        for (int i = 0; i < timings.keySet().size(); i++) {
            List<Integer> loginTimes = timings.get(i).keySet().stream().sorted().collect(Collectors.toList());

            int finalI = i;
            loginTimes.forEach(t -> dataset.addValue(timings.get(finalI).get(t), seriesList.get(finalI), t));
        }
        return dataset;
    }


    public static JFreeChart createCombinedMap(Map<Integer, Map<Integer, Integer>> timings, Map<Integer, Map<Integer, Integer>> boundaries,
                                               Map<Integer, List<Color>> colourMap, List<JavaMethod> analysedMethods) throws IOException {
        List<String> seriesNames = analysedMethods.stream().filter(JavaMethod::isActive).map(m -> m.getOdexOffsets().get(0)).collect(Collectors.toList());
        List<XYSeries> gtSeriesList = createDatasetGroundTruth(boundaries, seriesNames);
        List<XYSeries> prSeriesList = createDatasetPredictions(timings, seriesNames);
        gtSeriesList.addAll(prSeriesList);
        XYSeriesCollection dataset = new XYSeriesCollection();

        gtSeriesList.forEach(dataset::addSeries);
        JFreeChart chart = ChartFactory.createScatterPlot(
                "Ground Truth Binary Map",
                "Thread Counter Value", "Method Id", dataset);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.black);
        plot.setRangeGridlinePaint(Color.black);


        IntStream.range(0, gtSeriesList.size()).forEach(i ->
                plot.getRendererForDataset(plot.getDataset(0)).setSeriesPaint(i,
                        gtSeriesList.get(i).getKey().toString().startsWith("ad") ? Color.red : Color.blue)
        );


        NumberAxis axis = (NumberAxis) plot.getRangeAxis();
        axis.setTickUnit(new NumberTickUnit(1.0));
        return chart;
    }


    private static List<XYSeries> createDatasetGroundTruth(Map<Integer, Map<Integer, Integer>> boundaries, List<String> seriesNames) {

        XYSeries series1 = new XYSeries("Boys");
        series1.add(1, 72.9);
        series1.add(2, 81.6);
//        series1.add(3, 88.9);
        List<XYSeries> seriesList = IntStream.range(0, boundaries.size()).mapToObj(i -> new XYSeries("m_" + seriesNames.get(i))).collect(Collectors.toList());

        int nX = boundaries.size();

        for (int i = 0; i < nX; i++) {
            List<Integer> loginTimes = new ArrayList<>(boundaries.get(i).keySet());
            int finalI = i;
            loginTimes.forEach(t -> seriesList.get(finalI).add(t, Double.valueOf(boundaries.get(finalI).get(t) - 0.1)));
        }
        return seriesList;
    }

    private static List<XYSeries> createDatasetPredictions(Map<Integer, Map<Integer, Integer>> timings, List<String> seriesNames) {

        XYSeries series1 = new XYSeries("Boys");
        series1.add(1, 72.9);
        series1.add(2, 81.6);
//        series1.add(3, 88.9);
        List<XYSeries> seriesList = IntStream.range(0, timings.size()).mapToObj(i -> new XYSeries("ad_" + seriesNames.get(i))).collect(Collectors.toList());

        int nX = timings.size();

        for (int i = 0; i < nX; i++) {
            List<Integer> loginTimes = new ArrayList<>(timings.get(i).keySet());
            int finalI = i;
            loginTimes.forEach(t -> seriesList.get(finalI).add(t, timings.get(finalI).get(t)));
        }
        return seriesList;
    }

    private static Map<Integer, List<Color>> getColourLists(int timingSize, int boundariesSize) {
        List<String> allColoursString = new ArrayList<>();
        List<Color> allColours = new ArrayList<>();
        allColours.add(Color.red);
        allColours.add(Color.blue);
        allColours.add(Color.orange);
        allColours.add(Color.gray);
        allColours.add(Color.green);

        for (int c = 5; c < timingSize + boundariesSize + 4; c++) {
            int i = ThreadLocalRandom.current().nextInt(1, 256);
            int j = ThreadLocalRandom.current().nextInt(1, 256);
            int k = ThreadLocalRandom.current().nextInt(1, 256);
            String tempC = i + "#" + j + "#" + k + "#";
            if (allColoursString.contains(tempC)) {
                c--;
                continue;
            }
            allColoursString.add(tempC);
            allColours.add(new Color(i, j, k));
        }
        return new HashMap<>() {{
            put(0, allColours.subList(0, timingSize + 2));
            put(1, allColours.subList(timingSize + 2, timingSize + boundariesSize + 4));
        }};
    }

    public static String combineGraphsHr(List<String> graphNames) throws IOException {
        List<BufferedImage> bufferedImageList = graphNames.stream().map(File::new).map(input -> {
            try {
                return ImageIO.read(input);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());

        int heightTotal = bufferedImageList.get(0).getHeight();
        int widthTotal = bufferedImageList.stream().mapToInt(BufferedImage::getWidth).sum();

        int widthCurr = 0;
        BufferedImage concatImage = new BufferedImage(widthTotal, heightTotal, BufferedImage.TYPE_INT_RGB);
        for (BufferedImage image : bufferedImageList) {
            concatImage.createGraphics().drawImage(image, widthCurr, 0, null);
            widthCurr += image.getWidth();
        }
        String combTitle = graphNames.get(0).replace(".png", "").concat("_concat_") + System.currentTimeMillis() + ".png";
        ImageIO.write(concatImage, "png", new File(combTitle)); // export concat image
        return combTitle;
//        Runtime.getRuntime().exec("eog " + combTitle);

    }

}

