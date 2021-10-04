package odexanalyser;

import util.ConfigManager;
import util.PathManager;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OdexParser {
    static String packageName = "org.woheller69.weather.activit";

    static boolean filteringAddresses = false;
    final static List<Integer> scannedIds = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);

    static boolean limitedTargets = true;
    static int targetLimit = 2;

    static boolean stackMapFiltering = true;
    static int beginStackMap = 26;

    static boolean startingFromAddress = false;
    static int scanAddressBegin = 0;

    static boolean perStackMap = false;

    static Map<String, String> configMap = new HashMap<>();


    public static void main(String[] args) throws IOException {

        List<String> oatLines = Files.lines(Paths.get(PathManager.getOatFilePath()), StandardCharsets.ISO_8859_1)
                .collect(Collectors.toList());


        configMap = ConfigManager.readConfigs(PathManager.getConfigFilePath());
        scanAddressBegin = Integer.parseInt(configMap.get("scanAddressBegin"));
        beginStackMap = Integer.parseInt(configMap.get("beginStackMap"));
        targetLimit = Integer.parseInt(configMap.get("targetLimit"));

        Map<String, Integer> offsetIncs = new HashMap<>();

        Map<String, ScannedMethod> offsetMap = usingMethodOffsets(oatLines, offsetIncs);
        if (offsetMap.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.println("Extracting addresses from odex ...");

        List<String> methodList = new ArrayList<>(offsetMap.keySet());

        String mNameIdMapping = IntStream.range(0, methodList.size()).mapToObj(i -> i + "=" + methodList.get(i)).collect(Collectors.joining("|")).replaceAll(" \\(dex_method_idx=[0-9]+\\)", "").replaceAll("[0-9]+: ", "");
        ConfigManager.insertConfig(PathManager.getToolConfigFilePath(), "methodIdNameMapping", mNameIdMapping);
        String mAdMapping = IntStream.range(0, methodList.size()).mapToObj(i -> i + "=" + offsetMap.get(methodList.get(i)).getOffset()).collect(Collectors.joining("|"));
        ConfigManager.insertConfig(PathManager.getToolConfigFilePath(), "methodIdAdMapping", mAdMapping);
        String mSizeMapping = IntStream.range(0, methodList.size()).mapToObj(i -> i + "=" + offsetMap.get(methodList.get(i)).getSize()).collect(Collectors.joining("|"));
        ConfigManager.insertConfig(PathManager.getToolConfigFilePath(), "methodIdSizeMapping", mSizeMapping);

//        System.out.println(mAdMapping);

        List<String> offsets = offsetMap.values().stream().map(ScannedMethod::getOffset).collect(Collectors.toList());
        System.out.println("\n");
        System.out.println("Calculating distances ...");
        List<Integer> fixedIndexes = calcDistances(offsets);
        setExecOffset(oatLines);
        adjustThreshold(oatLines);

    }

    public static Map<String, ScannedMethod> usingMethodOffsets(List<String> lines, Map<String, Integer> offsetIncs) {

        Map<String, ScannedMethod> offsetMap = new LinkedHashMap<>();
        Map<String, Integer> methodSizeMap = new LinkedHashMap<>();
        List<String> curOffsets = new ArrayList<>();

        int configOffsetInc = Integer.parseInt(configMap.get("offsetInc"));
        int offsetMapSize = Integer.parseInt(configMap.get("offsetMapSize"));
        boolean methodBFound = false;

        int offsetStart = 20;
        int methodWidth = 0;
        for (int i = 0; i < lines.size()
                && offsetMap.size() < offsetMapSize
//                && !methodBFound
                ; i++) {
            String line = lines.get(i);
            if (isMethodBegin(line)) {
                int methodBeginId = i;
                int methodEndLineId;

//                  finding method end
                methodEndLineId = methodBeginId + 1;
                line = lines.get(methodEndLineId);
                while (!isMethodBegin(line) && !isClassBegin(line)) {

                    methodEndLineId++;
                    if (methodEndLineId == lines.size()) {
                        break;
                    }
                    line = lines.get(methodEndLineId);
                }

                methodEndLineId--;
//                  have found a method; need to verify whether it's relevant
                String methodName = lines.get(methodBeginId).strip();
                if (
                        !methodName.contains(packageName) ||
                                methodName.contains("aroundBody")
                                || methodName.contains("Lambda")
                                || methodName.contains("method1")
                                || methodName.contains("$")
                                || offsetMap.containsKey(methodName)) {
                    i = methodEndLineId;
                    continue;
                }
                int offsetInc = offsetIncs.getOrDefault(methodName, configOffsetInc);
                int offsetMapStartIndex = 0;
//                          verifying the method

//                  incrementing mBeginToNative
                while (!isNativeBegin(lines.get(methodBeginId)) && methodBeginId < methodEndLineId) {
                    methodBeginId++;
                }

                String offset = "";

                methodWidth = methodEndLineId - methodBeginId;
//                if (methodWidth < 400) {
//                    continue;
//                }
//                System.out.println("methodWidth=" + methodWidth);


//              inside a verified method
                for (int offsetId = methodEndLineId - 1; offsetId > methodBeginId; offsetId = offsetId - offsetInc) {
                    String offsetLine = lines.get(offsetId).strip();

                    if (offsetLine.startsWith("0x00")) {
//                                  0x008137d4: b940021f	ldr wzr, [x16]
                        String ins = offsetLine.split("\t")[1].split(" ")[0].trim();
                        offset = offsetLine.split(":")[0].replace("0x00", "");
                        if (!curOffsets.contains(offset)) {
                            if (
                                    methodName.contains("Child")
                                            || methodName.contains("RainViewerActivity")
                                            || methodName.contains("Splash")
//                                                || methodName.contains("activities")
//                                                || offsetMap.size() < offsetMapSize
                            ) {

                                offsetMap.put(methodName, new ScannedMethod(methodName, methodWidth, offset));
                                curOffsets.add(offset);
                            }
//                            offsetId = offsetId + offsetInc;
                            scanAddressBegin = offsetId;
                        }
                        break;

                    }

                }
                i = methodEndLineId;
//                System.out.println("methodWidth="+methodWidth);

            }
        }

        return offsetMap;
    }

    private static void adjustThreshold(List<String> oatDumpLines) throws IOException {

        String offset = "";
        for (int i = 100; i < oatDumpLines.size(); i++) {
            String line = oatDumpLines.get(i);

            if (isMethodBegin(line)) {
                if (line.contains("notUsedMethod")) {
                    int methodBeginId = i;
                    int methodEndLineId;

//                  finding method end
                    methodEndLineId = methodBeginId + 1;
                    line = oatDumpLines.get(methodEndLineId);
                    while (!isMethodBegin(line) && !isClassBegin(line)) {

                        methodEndLineId++;
                        if (methodEndLineId == oatDumpLines.size()) {
                            break;
                        }
                        line = oatDumpLines.get(methodEndLineId);
                    }

                    methodEndLineId--;

                    while (!isNativeBegin(oatDumpLines.get(methodBeginId)) && methodBeginId < methodEndLineId) {
                        methodBeginId++;
                    }

                    for (int offsetId = methodEndLineId - 10; offsetId > methodBeginId; offsetId = offsetId - 2) {
                        String offsetLine = oatDumpLines.get(offsetId).strip();

                        if (offsetLine.startsWith("0x00")) {
//                                  0x008137d4: b940021f	ldr wzr, [x16]
                            String ins = offsetLine.split("\t")[1].split(" ")[0].trim();
                            offset = offsetLine.split(":")[0].replace("0x00", "");
                            System.out.println("offset for adjusting threshold: " + offset);
                            System.out.println("width for adjusting threshold method: " + (methodEndLineId - methodBeginId));

                            ConfigManager.insertConfig(PathManager.getConfigFilePath(), "addressForAdjusting", offset);
                            return;
                        }

                    }
                }
            }
        }
    }

    private static boolean isMethodIncluded(String odexM, List<String> m2) {
        return m2.stream().anyMatch(m -> m.contains(odexM) && compareArgs(m, odexM));
    }

    private static boolean compareArgs(String odexM, String m2) {
        String[] odexMArgs = odexM.split("\\(")[1].split("\\)")[0].split(",");
        String[] m2Args = m2.split("\\(")[1].split("\\)")[0].split(",");
        return !IntStream.range(0, odexMArgs.length).anyMatch(i -> !odexMArgs[i].contains(m2Args[i]));

    }

    private static String getMatchingName(String qString, Map<String, Integer> mMIdMapping) {
        return mMIdMapping.keySet().stream().filter(k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE).matcher(qString).find()).findAny().get();
    }

    private static boolean isClassBegin(String line) {
        return line.contains("OatClassAllCompiled") || line.contains("OatClassSomeCompiled");
    }

    private static boolean isMethodBegin(String line) {
        return line.contains("dex_method_idx=");
    }

    private static boolean isNativeBegin(String line) {
        return line.contains("CODE: (code_offset=");
    }


    private static void setExecOffset(List<String> oatLines) {
        String execOffset = null;
        for (int i = 0; i < oatLines.size(); i++) {
            if (oatLines.get(i).contains("EXECUTABLE OFFSET")) {
                execOffset = oatLines.get(i + 1).replace("0x", "");
                break;
            }
        }
        if (execOffset != null) {
            ConfigManager.insertConfig(PathManager.getConfigFilePath(), "execOffset", execOffset);
        }
    }


    public static List<Integer> calcDistances(List<String> classOffsets) throws IOException {
        List<BigInteger> asDec = classOffsets.stream().sequential().map(String::strip).map(i -> new BigInteger(i, 16)).collect(Collectors.toList());
//        asDec.stream().sequential().forEach(i -> System.out.print(" " + i + " "));
        List<BigInteger> asDecSorted = asDec.stream().sequential().sorted().collect(Collectors.toList());

        List<Integer> indexes = asDec.stream().sequential().map(asDecSorted::indexOf).collect(Collectors.toList());
        for (int i = 0; i < indexes.size(); i++) {
            int cur = indexes.get(i);
            if (Collections.frequency(indexes, cur) > 1) {
                for (int j = 0; j < indexes.size(); j++) {
                    if (indexes.get(j) > cur) {
                        indexes.set(j, indexes.get(j) + 1);
                    }
                }
                for (int j = 0; j < indexes.size(); j++) {
                    if (i != j && indexes.get(j) == cur) {
                        indexes.set(j, indexes.get(j) + 1);
                    }
                }
            }
        }


        for (int j = 0; j < indexes.size(); j++) {
            if (indexes.contains(j)) {
                continue;
            }
            for (int k = j; true; k++) {
                if (indexes.contains(k)) {
                    indexes.set(indexes.indexOf(k), j);
                    break;
                }
            }
        }

        System.out.println("\noffsets in order");
        System.out.println(indexes.stream().map(classOffsets::get).collect(Collectors.joining(",")));
        System.out.println("\ndistances between offsets");
        IntStream.range(1, indexes.size()).forEach(i -> System.out.print((new BigInteger(classOffsets.get(indexes.indexOf(i)), 16))
                .subtract(new BigInteger(classOffsets.get(indexes.indexOf(i - 1)), 16)) + " "));
        System.out.println("\naddress count: " + indexes.size());
        List<String> toSideChannel = asDecSorted.stream().map(s -> s.toString(16)).collect(Collectors.toList());
        verifyWithSideChannelAddresses(toSideChannel, scannedIds);
        return indexes;
    }

    public static void verifyWithSideChannelAddresses(List<String> odexOffsets, List<Integer> scanIds) throws IOException {
        System.out.println("\n\nchecking sideChannel for scanning addresses ...");
        List<String> offsetsToBeScanned = new ArrayList<>(odexOffsets);


        boolean isSideChannelSet = false;
        boolean isOdexSet = false;

        String prevOffsets = ConfigManager.readConfigs(PathManager.getConfigFilePath()).get("sideChannelOffsets");
        String sideChannelLine = prevOffsets.split("000000")[1];

        StringJoiner sj = new StringJoiner(",", ",", "");
        offsetsToBeScanned.forEach(sj::add);


        if (!sideChannelLine.equals(sj.toString())) {
            System.out.println("sideChannel addresses were overridden");
            prevOffsets = prevOffsets.replace(sideChannelLine, sj.toString());
            ConfigManager.insertConfig(PathManager.getConfigFilePath(), "sideChannelOffsets", prevOffsets);
        } else {
            System.out.println("sideChannel addresses are in order");
        }


    }


}

class ScannedMethod {
    final private String name;
    final private Integer size;
    final private String offset;

    public ScannedMethod(String name, Integer size, String offset) {
        this.name = name;
        this.size = size;
        this.offset = offset;
    }

    public String getName() {
        return name;
    }

    public Integer getSize() {
        return size;
    }

    public String getOffset() {
        return offset;
    }
}




