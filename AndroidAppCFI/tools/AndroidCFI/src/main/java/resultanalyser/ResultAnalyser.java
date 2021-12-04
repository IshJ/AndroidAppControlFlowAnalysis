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
import org.jfree.util.ShapeUtilities;
import org.jgrapht.nio.ExportException;
import util.ConfigManager;
import util.PathManager;
import util.RuntimeExecutor;
import util.TimeUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static resultanalyser.StatCalculator.printPrintAndWrite;

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

    public static int floorVal = 0;
    public static int ceilingVal = 50;
    public static boolean onlyPositive = true;

    private static final String innerRecordSplitter = "\\|";

    private static final Color TRANSPARENT_COLOUR = new Color(0x00FFFFFF, true);

    private static final int concatIndex = 50;

    static boolean includeCFRules = false;

    private static boolean fromBackup = true;
    static String backUpFolderName = "1638502438846_2000";

    private static boolean backUpFiles = true;

    private static boolean createFlowDetectionPlot = false;

    private static boolean inMs = true;

    private static boolean boundaryOnly = false;

    public static List<String> stats = new ArrayList<>();

    public static List<String> tableRows = new ArrayList<>();
    public static final String columnSeparator = "#";

    public static void main(String[] args) {
        try {
            if (args.length > 0 && args[0].length() > 3) {
                fromBackup = true;
                backUpFolderName = args[0];
                System.out.println(backUpFolderName);
            }
            if (fromBackup && !backUpFolderName.isEmpty()) {
                backUpFolderName = PathManager.getDbFolderPath() + backUpFolderName + File.separator;
                PathManager.setSideChannelDbPath(backUpFolderName + PathManager.sideChannelFileName);
                PathManager.setGroundTruthDbPath(backUpFolderName + PathManager.groundTruthFileName);
                PathManager.setLogPath(backUpFolderName + PathManager.logFileName);
                PathManager.setOatFilePath(backUpFolderName + PathManager.oatFileName);
                PathManager.setConfigFilePath(backUpFolderName + PathManager.configFileName);
                PathManager.setToolConfigFilePath(backUpFolderName + PathManager.toolConfigFileName);
            }
            List<String> sideChannelLines = Files.lines(Paths.get(PathManager.getSideChannelDbPath()), StandardCharsets.ISO_8859_1).collect(Collectors.toList());
            List<String> groundTruthLines = Files.lines(Paths.get(PathManager.getGroundTruthDbPath()), StandardCharsets.ISO_8859_1).collect(Collectors.toList());

            if (inMs) {
                TimeUtil.calculateConverter(sideChannelLines);
            }


            Map<String, String> toolConfigMap = ConfigManager.readConfigs(PathManager.getToolConfigFilePath());
            Map<String, String> configMap = ConfigManager.readConfigs(PathManager.getConfigFilePath());
            ceilingVal = Integer.parseInt(toolConfigMap.get("ceilVal"));
            floorVal = Integer.parseInt(toolConfigMap.get("floorVal"));

            filterSideChannelRecords(sideChannelLines, groundTruthLines, aroundGroundTruths, hitCounterBased);

            List<String> logLines = Files.lines(Paths.get(PathManager.getLogPath()), StandardCharsets.ISO_8859_1).collect(Collectors.toList());
            List<String> filteredSideChannelLines = Files.lines(Paths.get(PathManager.getFilteredOutputPath()), StandardCharsets.ISO_8859_1).collect(Collectors.toList());


            title = title + "_" + (toolConfigMap.get("threshold") == null ? "" : toolConfigMap.get("threshold")) + "_";


            if ("0".equals(toolConfigMap.get("parseLogs"))) {
                return;
            }


            String baseAddress = getBaseAddress(logLines);
//            map containing the mapping from odex method ids to aop methodmap ids.
            Map<Integer, Integer> odexToAppMethodIdMap = resolveMethods(toolConfigMap);
//            32,33,2
            List<Integer> activeOdexM = new ArrayList<>(odexToAppMethodIdMap.keySet());


            List<JavaMethod> analysedMethods = getAnalysedMethods(odexToAppMethodIdMap, toolConfigMap, configMap);
            List<String> seriesNames = getSeriesNames(analysedMethods, activeOdexM);
            String printText = "mapped methods\n>" + analysedMethods.stream().filter(JavaMethod::isActive).map(JavaMethod::getOdexName).collect(Collectors.joining("\n>")) + "\n";
            printResult(printText);
            String graphName = generateGraph(groundTruthLines, filteredSideChannelLines, seriesNames, title, analysedMethods);
            try {
                executePythonScript();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (backUpFiles) {
                backupData(graphName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Files.write(Path.of(PathManager.getStatsPath()), stats, StandardCharsets.ISO_8859_1, StandardOpenOption.APPEND);
            printPrintAndWrite(tableRows);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void executePythonScript() throws IOException, InterruptedException {
        RuntimeExecutor.runCommand("python3 " + PathManager.getScriptFolderPath() + "heatMapGenerator.py", true);
    }


    private static void backupData(String graphName) {
        backUpFolderName = "" + System.currentTimeMillis();
        File backupPath = new File(PathManager.getDbFolderPath() + backUpFolderName);
        String backupPathString = PathManager.getDbFolderPath() + backUpFolderName + File.separator;
        backupPath.mkdir();
        try {

            Files.copy(new File(PathManager.getSideChannelDbPath()).toPath(), new File(backupPathString + "side_channel_info_full.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getGroundTruthDbPath()).toPath(), new File(backupPathString + "ground_truth_full.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getProcessedRecordsPath()).toPath(), new File(backupPathString + "processedRecords.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getChartWorkerRecordsPath()).toPath(), new File(backupPathString + "chartWorkerRecords.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getCfRulesFilePath()).toPath(), new File(backupPathString + "cfRules.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getGroundTruthGraphFilePath()).toPath(), new File(backupPathString + "groundTruthGraph.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getPredictionGraphFilePath()).toPath(), new File(backupPathString + "predictionGraph.out").toPath(), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(new File(PathManager.getLogPath()).toPath(), new File(backupPathString + "log.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getOatFilePath()).toPath(), new File(backupPathString + "oatdump.out").toPath(), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(new File(PathManager.getConfigFilePath()).toPath(), new File(backupPathString + "config.out").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getToolConfigFilePath()).toPath(), new File(backupPathString + "toolConfig.out").toPath(), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(new File(PathManager.getGraphFolderPath() + "groundTruthFLow.svg").toPath(), new File(backupPathString + "groundTruthFLow.svg").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(PathManager.getGraphFolderPath() + "PredictedFLow.svg").toPath(), new File(backupPathString + "PredictedFLow.svg").toPath(), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(new File(PathManager.getGraphFolderPath() + "heatmap.png").toPath(), new File(backupPathString + "heatmap.png").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(graphName).toPath(), new File(backupPathString + "scatterPlot.png").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new File(graphName).toPath(), new File(backupPathString + "scatterPlot.png").toPath(), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(new File(PathManager.getStatsPath()).toPath(), new File(backupPathString + "resultAnalysis.out").toPath(), StandardCopyOption.REPLACE_EXISTING);


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    static List<JavaMethod> getAnalysedMethods(Map<Integer, Integer> odexToAppMethodIdMap, Map<String, String> toolConfigMap, Map<String, String> configMap) throws IOException {
        List<JavaMethod> methods = new ArrayList<>();
        Map<Integer, String> methodIdAdMapping = Arrays.stream(toolConfigMap.get("methodIdAdMapping").split("\\|"))
                .filter(s -> s.contains("="))
                .collect(Collectors.toMap(
                        s -> Integer.parseInt(s.split("=")[0]), s -> s.split("=")[1]
                ));

        Map<String, Integer> revMethodIdAdMapping = methodIdAdMapping.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        Map<Integer, String> methodIdNameMapping = Arrays.stream(toolConfigMap.get("methodIdNameMapping").split("\\|"))
                .filter(s -> s.contains("="))
                .collect(Collectors.toMap(
                        s -> Integer.parseInt(s.split("=")[0].strip()), s -> s.split("=")[1].strip()
                ));
        Map<Integer, Integer> methodIdSizeMapping = Arrays.stream(toolConfigMap.get("methodIdSizeMapping").split("\\|"))
                .filter(s -> s.contains("="))
                .collect(Collectors.toMap(
                        s -> Integer.parseInt(s.split("=")[0]), s -> Integer.parseInt(s.split("=")[1])
                ));

        Map<Integer, String> appMethodIdNameMapping = Arrays.stream(toolConfigMap.get("MethodMap").split("\\|"))
                .filter(s -> s.contains("="))
                .collect(Collectors.toMap(
                        s -> Integer.parseInt(s.split("=")[1].strip()), s -> s.split("=")[0].strip()
                ));

        List<String> odexOffsets = new ArrayList<>(List.of(configMap.get("sideChannelOffsets").split(",")));
        List<String> appOffsets = new ArrayList<>(List.of(toolConfigMap.get("offsets").split(",")));

        if (odexOffsets.size() != appOffsets.size()) {
            System.out.println("odexOffsets length != appOffsets length! (" + odexOffsets.size() + "," + appOffsets.size() + ")");
            System.exit(0);
        }

        odexOffsets.remove(0);
        appOffsets.remove(0);

        Map<String, List<String>> cfRuleMap = new HashMap<>();

        if (includeCFRules) {
            List<String> cfRules = Files.lines(Paths.get(PathManager.getCfRulesFilePath()), StandardCharsets.ISO_8859_1).collect(Collectors.toList());
            Map<Integer, String> mNameIndMap = List.of(cfRules.get(0).split("\\|")).stream().collect(Collectors.toMap(
                    s -> Integer.valueOf(s.split("-")[1]), s -> s.split("-")[0]
            ));
            cfRules.remove(0);
            mNameIndMap.values().forEach(v -> cfRuleMap.put(v, new ArrayList<>()));
            cfRules.forEach(l -> {
                String[] splits = l.split("->");
                cfRuleMap.get(mNameIndMap.get(Integer.valueOf(splits[0]))).add(mNameIndMap.get(Integer.valueOf(splits[1])));
            });
        }

        int curId = 0;
        for (int i = 0; i < odexOffsets.size(); i++) {
//            7e67ej
            String odexOffset = odexOffsets.get(i);
            Integer odexId = revMethodIdAdMapping.entrySet().stream().filter(e -> e.getKey().contains(odexOffset)).findFirst().get().getValue();
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
        if (includeCFRules) {
            methods.stream()
                    .filter(m -> cfRuleMap.keySet().stream().filter(s -> m.getAppName().contains("s")).anyMatch(k -> !cfRuleMap.get(k).isEmpty()))
                    .forEach(m -> {
                        cfRuleMap.entrySet().stream().filter(e -> m.getAppName().contains(e.getKey())).findAny().get().getValue()
                                .stream().map(s -> methods.stream().filter(m1 -> m1.getAppName().contains(s)).findAny().get())
                                .collect(Collectors.toList()).forEach(mC -> {
                            m.addChild(mC);
                            mC.addParent(m);
                        });

                    });

        }

        return methods;
    }


    public static String generateGraph(List<String> groundTruthLines, List<String> sideChannelLines,
                                       List<String> seriesNames,
                                       String title, List<JavaMethod> analysedMethods) throws IOException {
        Map<Integer, Map<Integer, Integer>> boundaries = parseForBoundaries(groundTruthLines, true, analysedMethods);
        Map<Integer, Map<Integer, Integer>> timings = parseForAddress(sideChannelLines, true, analysedMethods);

        Map<Integer, Integer> timingToBoundary = timings.keySet().stream().collect(Collectors.toMap(k -> k, k -> {
            if (boundaries.containsKey(k)) {
                return k;
            } else {
                int appMethodId = analysedMethods.stream().filter(m -> m.getLogParserId() == k).findAny().get().getAppMethodId();
                return analysedMethods.stream().filter(m -> m.getAppMethodId() == appMethodId && boundaries.containsKey(m.getLogParserId())).findAny().get().getLogParserId();

            }
        }));


//        writeForHeatMap(boundaries, timings, timingToBoundary);
//        writeForChartWorker(boundaries, timings, seriesNames, timingToBoundary);
        if (includeCFRules) {
            generateCFCharts(boundaries, timings, analysedMethods);
        }
        StatCalculator.calculateScoresForWindowCountBased(boundaries, timings, analysedMethods, ceilingVal, timingToBoundary);
//        if (fromBackup) {
//            return "";
//        }
        String graphName = "";
        Map<Integer, List<Color>> colourMap = getColourLists(timings.size(), boundaries.size());

        List<Integer> timeKeys = timings.keySet().stream()
                .map(k -> new ArrayList<>(timings.get(k).keySet())).collect(Collectors.toList()).stream()
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        timeKeys.remove(timeKeys.size() - 1);

        try {
            Map<Integer, Map<Integer, Integer>> tempTimings = new HashMap<>();
            Map<Integer, Map<Integer, Integer>> tempBoundaries = new HashMap<>();
            List<String> graphNames = new ArrayList<>();
            if (isFilteringMethods) {
                IntStream.range(0, filterMethodIds.size()).forEach(i -> tempBoundaries.put(i, new HashMap<>()));
            } else {
                boundaries.keySet().forEach(i -> tempBoundaries.put(i, new HashMap<>()));
            }

            if (allAddressesInOneGraph) {
                if (isFilteringAddresses) {
                    IntStream.range(0, filterAddressIds.size()).forEach(i -> tempTimings.put(i, new HashMap<>()));
                } else {
                    timings.keySet().forEach(i -> tempTimings.put(i, new HashMap<>()));

                }
            } else {
                tempTimings.put(0, new HashMap<>());
            }

            int graphSize = timeKeys.size() / splitCount;
            for (int i = 0; i < splitCount; i++) {
                List<Integer> keys = timeKeys.subList(i * graphSize, (i + 1) * graphSize);
                if (isFilteringMethods) {
                    IntStream.range(0, filterMethodIds.size()).forEach(bkey ->
                            keys.forEach(k -> tempBoundaries.get(timingToBoundary.get(bkey)).put(k, filterMethodIds.get(bkey))));
                    tempTimings.keySet().forEach(tkey -> keys.forEach(k -> tempTimings.get(tkey).put(k, timings.get(filterAddressIds.get(tkey)).get(k))));

                } else {
                    tempBoundaries.keySet()
                            .forEach(bkey -> boundaries.get(bkey).keySet()
                                    .forEach(k -> tempBoundaries.get(bkey).put(k, boundaries.get(bkey).get(k))));
                    tempTimings.keySet()
                            .forEach(tkey -> timings.get(tkey).keySet()
                                    .forEach(k -> tempTimings.get(tkey).put(k, timings.get(tkey).get(k))));
                }

                graphName = PathManager.getGraphFolderPath() + title + i;
                if (splitCount == 1 && !combineGraphs) {
                    graphName = PathManager.getGraphFolderPath() + title + i;
                }

                graphName = createCombGraph(tempTimings, tempBoundaries, colourMap, graphName, analysedMethods, timingToBoundary);
                if (combineGraphs) {
                    graphNames.add(graphName);
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
                                          Map<Integer, List<Color>> colourMap, String graphName, List<JavaMethod> analysedMethods, Map<Integer, Integer> timingToBoundary) throws IOException {
//        ChartWorker.createTimeLine(tempTimings, tempBoundaries, analysedMethods);
        JFreeChart chartGt = createCombinedMap(tempTimings, tempBoundaries, colourMap, analysedMethods, timingToBoundary);
        graphName = graphName + "_gt_" + System.currentTimeMillis() + ".png";
        ChartUtils.saveChartAsPNG(new File(graphName), chartGt, 5000, 800);
        return graphName;
    }


    static Map<Integer, Map<Integer, Integer>> parseForBoundaries(List<String> lines, boolean isGenerateFraph, List<JavaMethod> analysedMethods) {
        System.out.println("parsing boundaries..");
        Map<Integer, Map<Integer, Integer>> boundaries = new HashMap<>();
        Map<Integer, Integer> appAddressToId = analysedMethods.stream().filter(JavaMethod::isActive).collect(Collectors.toMap(
                JavaMethod::getAppMethodId, JavaMethod::getLogParserId, (key1, key2) -> key1 < key2 ? key1 : key2
        ));
        appAddressToId.values().forEach(i -> boundaries.put(i, new HashMap<>()));
        int prevEnd = 0;
        for (String line : lines) {

            String[] splits = line.split(innerRecordSplitter);
            String methodInfo = splits[1];
            int timingCount = Integer.parseInt(splits[2]);
            int startTimingCount = Integer.parseInt(splits[1]);

            int endTimingCount = Integer.parseInt(splits[2]);
            if (inMs) {
                startTimingCount = Math.toIntExact(TimeUtil.getTimeInMs(startTimingCount));
                endTimingCount = Math.toIntExact(TimeUtil.getTimeInMs(endTimingCount));
            }
            int methodId = Integer.parseInt(splits[0]);
            if (startTimingCount == -1 || !appAddressToId.containsKey(methodId)) {
                continue;
            }
            methodId = appAddressToId.get(methodId);
            if (!boundaries.containsKey(methodId)) {
                boundaries.put(methodId, new HashMap<>());
            }

//          adjustment for overlapping
//            if (prevEnd == startTimingCount) {
//                startTimingCount += 2;
//            }
//            boundaries.get(methodId).put((startTimingCount+endTimingCount)/2, methodId);
//            boundaries.get(methodId).put(startTimingCount, 0);
            boundaries.get(methodId).put(startTimingCount, endTimingCount);
//            boundaries.get(methodId).put(endTimingCount, 1);

        }


        System.out.println("done parsing boundaries\n");
        return boundaries;
    }

    //mid-> (time-> value)
    static Map<Integer, Map<Integer, Integer>> parseForAddress(List<String> lines, boolean isGenerateFraph, List<JavaMethod> analysedMethods) {
        System.out.println("parsing timings..");
//        1623198431683|364|521462870688|198109
        Map<String, Integer> appAddressToId = analysedMethods.stream().filter(JavaMethod::isActive).collect(Collectors.toMap(m -> m.getAppOffsets().get(0), JavaMethod::getLogParserId));

        Map<Integer, Map<Integer, Integer>> timings = new HashMap<>();

        appAddressToId.values().forEach(i -> timings.put(i, new HashMap<>()));

        for (String line : lines) {
            String[] splits = line.split("\\|");
            Integer timingCount = Integer.parseInt(splits[3]);
            if (inMs) {
                timingCount = Math.toIntExact(TimeUtil.getTimeInMs(timingCount));
            }


            if (!appAddressToId.containsKey(splits[2])) {
                continue;
            }
            int methodId = appAddressToId.get(splits[2]);
            int timing = Integer.parseInt(splits[1]);

//          filtering for specific methods
            if (isFilteringAddresses && !filterAddressIds.contains(methodId)) {
                continue;
            }

            if (plotForAllTimes || (timing < ceilingVal && timing > floorVal)) {
                timings.get(methodId).put(timingCount, methodId);
            }
        }
        Map<Integer, Long> freqFilterMap = new HashMap<>();
        StringBuilder sb = new StringBuilder();

        timings.keySet().forEach(m -> freqFilterMap.put(m, timings.get(m).values().stream().count()));
        freqFilterMap.keySet().stream().map(m -> "address " + m + " -> " + freqFilterMap.get(m) + " hits").forEach(System.out::println);

        System.out.println("\ndone parsing timings\n");
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
        List<String> matchedMethodNames = new ArrayList<>();
        for (String appM : appMethods) {
            String matchingMethod = isMethodIncluded(appM, odexMethods);
            if (!matchingMethod.isEmpty()) {
                odexToApp.put(odexAddressIdMapping.get(matchingMethod), appAddressIdMapping.get(appM));
                matchedMethodNames.add(matchingMethod);
            }
        }
        String printText = "mapped method count: " + odexToApp.size();
        printResult(printText);


        return odexToApp;

    }

    private static String isMethodIncluded(String appM, List<String> m2) {

        return m2.stream().
                filter(m -> appM.split("\\(")[0].split(" ")[1].contains(m.split("\\(")[0].split(" ")[1])
//                        && compareArgs(appM.split("\\(")[1].split("\\)"), m.split("\\(")[1].split("\\)"))
                ).
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


    private static List<String> getSeriesNames(List<JavaMethod> analysedMethods, List<Integer> activeOdexM) {

        return activeOdexM.stream().map(i -> analysedMethods.stream().filter(m -> m.getOdexMethodId() == i).findAny().get().getShortName()).collect(Collectors.toList());

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
                if (timing < ceilingVal && timing > floorVal) {
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
                return timing < ceilingVal && timing > floorVal && (!aroundGroundTruths || (timing > groundTruthBegin - 10 && timing < groundTruthEnd + 10));
            }).forEach(filteredLines::add);
        }
        String printText = "Number of records: " + filteredLines.size();
        printResult(printText);

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

    private static void generateCFCharts(Map<Integer, Map<Integer, Integer>> boundaries, Map<Integer, Map<Integer, Integer>> timings,
                                         List<JavaMethod> analysedMethods) throws IOException {
        Map<Integer, JavaMethod> methodIdMap = analysedMethods.stream().collect(Collectors.toMap(JavaMethod::getLogParserId, jm -> jm));

        List<List<String>> boundaryNodes = writeDotGraphFile(methodIdMap, boundaries, PathManager.getGroundTruthGraphFilePath());
        List<Integer> boundaryPreds = boundaryNodes.stream().filter(l -> l.size() > 2)
                .map(l -> Integer.parseInt(l.get(0).split("_")[1].strip()))
                .collect(Collectors.toList());
        List<List<String>> timingNodes = writeDotGraphFile(methodIdMap, timings, PathManager.getPredictionGraphFilePath());
        List<Integer> timingPreds = timingNodes.stream().filter(l -> l.size() > 2)
                .map(l -> Integer.parseInt(l.get(0).split("_")[1].strip()))
                .collect(Collectors.toList());

        renderHrefGraph(PathManager.getGroundTruthGraphFilePath(), PathManager.getGraphFolderPath() + "groundTruthFLow.svg");
        renderHrefGraph(PathManager.getPredictionGraphFilePath(), PathManager.getGraphFolderPath() + "PredictedFLow.svg");

        if (createFlowDetectionPlot) {
            XYSeries timingSeries = new XYSeries("predicted flows");
            XYSeries boundarySeries = new XYSeries("real flows");

            boundaryPreds.forEach(i -> boundarySeries.add(i, Double.valueOf(0.0)));
            timingPreds.forEach(i -> timingSeries.add(i, Double.valueOf(1.0)));

            XYSeriesCollection dataset = new XYSeriesCollection();
            dataset.addSeries(timingSeries);
            dataset.addSeries(boundarySeries);

            JFreeChart chart = ChartFactory.createScatterPlot(
                    "Flow detection",
                    "Thread Counter Value", "", dataset);

            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(Color.white);
            plot.setDomainGridlinePaint(Color.black);
            plot.setRangeGridlinePaint(Color.black);

            NumberAxis axis = (NumberAxis) plot.getRangeAxis();
            axis.setTickUnit(new NumberTickUnit(1.0));
            axis.setRange(-1, 2.0);


            ChartUtils.saveChartAsPNG(new File(PathManager.getConfigFolderPath() + "flowGraph.png"), chart, 5000, 800);

        }


        int a = 3;


//        predicted flow
    }

    private static List<List<String>> writeDotGraphFile(Map<Integer, JavaMethod> methodIdMap, Map<Integer, Map<Integer, Integer>> methodTimings, String filePath) throws IOException {
        List<Integer> timeKeys = methodTimings.keySet().stream()
                .map(k -> new ArrayList<>(methodTimings.get(k).keySet())).collect(Collectors.toList()).stream()
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        //        ground truth flow

        List<Map<JavaMethod, Integer>> methodSeq = timeKeys.stream().sequential()
                .map(t -> methodTimings.keySet().stream().filter(mId -> methodTimings.get(mId).keySet().contains(t))
//                        .map(methodIdMap::get)
                        .collect(Collectors.toMap(methodIdMap::get, k -> t)))
                .collect(Collectors.toList());

        List<List<String>> graphNodeList = new ArrayList<>();
        for (Map<JavaMethod, Integer> curMethodMap : methodSeq) {
            for (JavaMethod curMethod : curMethodMap.keySet()) {

//            JavaMethod curMethod = curMethodMap.
                List<String> parents = curMethod.getParentMethods().stream().map(JavaMethod::getShortName).collect(Collectors.toList());
                if (!parents.isEmpty()) {
                    Optional<List<String>> parentList = graphNodeList.stream().filter(m -> parents.contains(getLastElement(m).split("_")[0])).findFirst();
                    if (parentList.isPresent()) {
                        parentList.get().add(curMethod.getShortName() + "_" + curMethodMap.get(curMethod));
                        continue;
                    }
                }
                graphNodeList.add(new ArrayList<>(List.of(curMethod.getShortName() + "_" + curMethodMap.get(curMethod))));
            }
        }

        int counter = 0;
        List<String> printLines = new ArrayList<>();
        printLines.add("strict digraph G {");
        for (List<String> nodeList : graphNodeList) {
            int finalCounter = counter;
            nodeList.forEach(n -> printLines.add(getGraphFormat(n, finalCounter)));
//            printLines.add(getGraphFormat(nodeList.get(0), counter));
            if (nodeList.size() > 1) {
                IntStream.range(1, nodeList.size()).forEach(i -> printLines.add(nodeList.get(i - 1) + finalCounter + " -> " + nodeList.get(i) + finalCounter + ";"));
            }
            counter++;

        }
        printLines.add("}");

        Files.write(Path.of(filePath), printLines, StandardCharsets.ISO_8859_1);
        return graphNodeList;
    }

    private static String getGraphFormat(String javaMethod, int c) {
        return javaMethod + c + " [ label=\"" + javaMethod + "\" ];";
    }

    private static void writeForHeatMap(Map<Integer, Map<Integer, Integer>> boundaries, Map<Integer, Map<Integer, Integer>> timings, Map<Integer, Integer> timingToBoundary) throws IOException {

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
                    if (boundaries.containsKey(mId)) {
                        addAll(boundaries.get(mId).keySet());
                    }
                }
            });
            mTimingList = mTimingList.stream().sorted().collect(Collectors.toList());
            int max = mTimingList.get(mTimingList.size() - 1) - concatIndex;

            for (int i = 0; i < max; i = i + concatIndex) {
                int finalI = i;
                int timingCount = (int) mTimingList.stream().filter(t -> t > finalI && t < finalI + concatIndex)
                        .filter(t -> timings.get(mId).containsKey(t)).count();
                int boundaryCount = (int) mTimingList.stream().filter(t -> t > finalI && t < finalI + concatIndex)
                        .filter(t -> boundaries.get(timingToBoundary.get(mId)).containsKey(t)).count();
                records.add(formatToRecord(List.of(i / concatIndex, mId, Math.min(timingCount, 10), boundaryCount)));

            }

        }
        System.out.println("records size: " + records.size());
        Files.write(Path.of(PathManager.getProcessedRecordsPath()), records, StandardCharsets.ISO_8859_1);


    }

    private static void writeForChartWorker(Map<Integer, Map<Integer, Integer>> boundaries, Map<Integer, Map<Integer, Integer>> timings,
                                            List<String> seriesNames, Map<Integer, Integer> timingToBoundary) throws IOException {

        List<String> records = new ArrayList<>();
        records.add("seriesNames:" + String.join(",", seriesNames));

        records.add("==timings===");
        List<Integer> methodIDList = new ArrayList<>(timings.keySet());
        methodIDList.forEach(m -> records.add("timings_" + m + ":" + timings.get(m).keySet().stream()
                .map(String::valueOf).collect(Collectors.joining(","))));
        records.add("==boundaries===");
        methodIDList.forEach(m -> records.add("boundaries_" + m + ":" + boundaries.get(timingToBoundary.get(m)).keySet().stream()
                .map(String::valueOf).collect(Collectors.joining(","))));

        Files.write(Path.of(PathManager.getChartWorkerRecordsPath()), records, StandardCharsets.ISO_8859_1);
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
                                               Map<Integer, List<Color>> colourMap, List<JavaMethod> analysedMethods,
                                               Map<Integer, Integer> timingToBoundary) throws IOException {
        List<String> seriesNames = analysedMethods.stream().filter(JavaMethod::isActive).map(m -> m.getShortName() + "_" + m.getLogParserId()).collect(Collectors.toList());
//        List<String> seriesNames = analysedMethods.stream().filter(JavaMethod::isActive).map(m -> m.getOdexOffsets().get(0)).collect(Collectors.toList());
//        List<String> seriesNames = analysedMethods.stream().filter(JavaMethod::isActive).map(JavaMethod::getLogParserId).map(String::valueOf).collect(Collectors.toList());
        List<XYSeries> gtSeriesList = createDatasetGroundTruth(boundaries, seriesNames);
        List<XYSeries> prSeriesList = createDatasetPredictions(timings, seriesNames, timingToBoundary);
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

        Line2D line = new Line2D.Float(1f, 1f, 1f, 1f);


        IntStream.range(0, gtSeriesList.size()).forEach(i ->
                {

                    String seriesName = gtSeriesList.get(i).getKey().toString();
                    Color c;
                    Shape s = ShapeUtilities.createLineRegion(line, 7);
                    if (seriesName.startsWith("ad")) {
                        c = Color.red;
                        s = ShapeUtilities.createDiagonalCross(3, 1);
                        plot.getRendererForDataset(plot.getDataset(0)).setSeriesShape(i, s);
                    } else if (seriesName.startsWith("m_b")) {
                        c = Color.lightGray;
                        plot.getRendererForDataset(plot.getDataset(0)).setSeriesShape(i, s);
                    } else c = Color.blue;
                    plot.getRendererForDataset(plot.getDataset(0)).setSeriesPaint(i, c);
                }

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


        int nX = boundaries.size();
        List<Integer> boundaryKeys = boundaries.keySet().stream().sorted().collect(Collectors.toList());


        List<XYSeries> seriesList = boundaryKeys.stream().map(i -> new XYSeries("m_" + seriesNames.get(i).split("_")[0])).collect(Collectors.toList());
        List<XYSeries> seriesListBoundary = boundaryKeys.stream().map(i -> new XYSeries("m_boundary_" + seriesNames.get(i).split("_")[0])).collect(Collectors.toList());

        for (int i = 0; i < nX; i++) {
            int boundaryKey = boundaryKeys.get(i);
            int finalI = i;
            List<Integer> loginTimes = new ArrayList<>(boundaries.get(boundaryKey).keySet());
            List<Integer> loginTimesBoundary = boundaries.get(boundaryKey).keySet().stream()
                    .map(k -> IntStream.range(k, boundaries.get(boundaryKey).get(k))
                            .filter(x -> x % 1 == 0)
                            .boxed().collect(Collectors.toList()))
                    .collect(Collectors.toList())
                    .stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());


            loginTimes.forEach(t -> seriesList.get(finalI).add(t, Double.valueOf(finalI - 0.1)));
            loginTimes.forEach(t -> seriesList.get(finalI).add(boundaries.get(boundaryKey).get(t), Double.valueOf(finalI - 0.1)));

//            double dif = boundaryOnly ? 0.1 : 0.2;
            double dif = 0.1;
//            dif=0.1;
            loginTimesBoundary.forEach(t -> seriesListBoundary.get(finalI).add(t, Double.valueOf(finalI - dif)));

        }
        if (boundaryOnly) {
            return seriesListBoundary;
        }
        seriesList.addAll(seriesListBoundary);
        return seriesList;
    }

    private static List<XYSeries> createDatasetPredictions(Map<Integer, Map<Integer, Integer>> timings, List<String> seriesNames, Map<Integer, Integer> timingToBoundary) {

        XYSeries series1 = new XYSeries("Boys");
        series1.add(1, 72.9);
        series1.add(2, 81.6);
//        series1.add(3, 88.9);
        List<XYSeries> seriesList = IntStream.range(0, timings.size()).mapToObj(i -> new XYSeries("ad_" + seriesNames.get(i))).collect(Collectors.toList());
        List<Integer> boundaryKeys = timingToBoundary.values().stream().sorted().collect(Collectors.toList());

        int nX = timings.size();

        for (int i = 0; i < nX; i++) {
            List<Integer> loginTimes = new ArrayList<>(timings.get(i).keySet());
            int finalI = i;
            int mulFactor = (int) timingToBoundary.keySet().stream().filter(k -> timingToBoundary.get(k) == timingToBoundary.get(finalI)).count();
            loginTimes.forEach(t -> seriesList.get(finalI).add(t, Double.valueOf((0.1 * timings.get(finalI).get(t) + 0.9 * timingToBoundary.get(finalI) / mulFactor))));
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

    private static void renderHrefGraph(String graphFile, String outFile)
            throws ExportException {

        try {
//            String outFile = PathManager.getConfigFolderPath()+"output7.svg";
            String cmd = "dot -Tsvg " + graphFile + " -o" + outFile;
//            String cmd = "echo 'digraph { a -> b }' | dot -Tsvg -o"+ PathManager.getConfigFolderPath()+"output2.svg" ;
            RuntimeExecutor.runCommand(cmd, false);
//            RuntimeExecutor.showImage(outFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static <T> T getLastElement(List<T> itemList) {
        return itemList.get(itemList.size() - 1);
    }

    public static void printResult(String text){
        System.out.println(text);
        stats.add(text);
    }

}

