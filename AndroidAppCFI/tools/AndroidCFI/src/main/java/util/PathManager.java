package util;

import java.io.File;

public class PathManager {
    static String rootFolderPath = System.getProperty("user.dir")
            .replace("tools" + File.separator + "AndroidCFI", "")
            .replace("scripts", "");
    ;

    static String dbFolderPath = rootFolderPath + "db" + File.separator;
    static String graphFolderPath = rootFolderPath + "graphs" + File.separator;
    static String metricsFolderPath = rootFolderPath + "metrics" + File.separator;
    static String scriptFolderPath = rootFolderPath + "scripts" + File.separator;
    static String configFolderPath = rootFolderPath + "config" + File.separator;

    static String groundTruthDbPath = dbFolderPath + "ground_truth_full.out";
    public final static String groundTruthFileName = "ground_truth_full.out";
    static String sideChannelDbPath = dbFolderPath + "side_channel_info_full.out";
    public final static String sideChannelFileName = "side_channel_info_full.out";
    static String filteredOutputPath = dbFolderPath + "filtered.out";
    static String processedRecordsPath = dbFolderPath + "processedRecords.out";
    static String chartWorkerRecordsPath = dbFolderPath + "chartWorkerRecords.out";

    static String logPath = rootFolderPath + "log.out";
    public final static String logFileName = "log.out";
    static String oatFilePath = rootFolderPath + "oatdump.out";
    public final static String oatFileName = "oatdump.out";

    static String addressStatsOut = metricsFolderPath + "addressStats.out";
    static String methodStatsOut = metricsFolderPath + "methodStats.out";
    static String statsPath = metricsFolderPath + "resultAnalysis.out";
    static String odexStatsPath = metricsFolderPath + "odexStats.out";
    static String excelFormatDataPath = metricsFolderPath + "excelFormatData.out";

    static String toolConfigFilePath = configFolderPath + "toolConfig.out";
    public final static String toolConfigFileName =  "toolConfig.out";
    static String configFilePath = configFolderPath + "config.out";
    public final static String configFileName = "config.out";
    static String cfRulesFilePath = configFolderPath + "cfRules.out";
    static String scannedMethodsFilePath = configFolderPath + "scannedMethods.out";


    static String groundTruthGraphFilePath = graphFolderPath + "groundTruthGraph.out";
    static String predictionGraphFilePath = graphFolderPath + "predictionGraph.out";

    public static String getRootFolderPath() {
        return rootFolderPath;
    }

    public static String getDbFolderPath() {
        return dbFolderPath;
    }

    public static String getGraphFolderPath() {
        return graphFolderPath;
    }

    public static String getMetricsFolderPath() {
        return metricsFolderPath;
    }

    public static String getScriptFolderPath() {
        return scriptFolderPath;
    }

    public static String getConfigFolderPath() {
        return configFolderPath;
    }

    public static String getGroundTruthDbPath() {
        return groundTruthDbPath;
    }

    public static String getSideChannelDbPath() {
        return sideChannelDbPath;
    }

    public static String getFilteredOutputPath() {
        return filteredOutputPath;
    }

    public static String getProcessedRecordsPath() {
        return processedRecordsPath;
    }

    public static String getChartWorkerRecordsPath() {
        return chartWorkerRecordsPath;
    }

    public static String getStatsPath() {
        return statsPath;
    }

    public static String getOdexStatsPath() {
        return odexStatsPath;
    }

    public static String getExcelFormatDataPath() {
        return excelFormatDataPath;
    }

    public static String getLogPath() {
        return logPath;
    }

    public static String getOatFilePath() {
        return oatFilePath;
    }

    public static String getAddressStatsOut() {
        return addressStatsOut;
    }

    public static String getMethodStatsOut() {
        return methodStatsOut;
    }

    public static String getToolConfigFilePath() {
        return toolConfigFilePath;
    }

    public static String getConfigFilePath() {
        return configFilePath;
    }

    public static String getScannedMethodsFilePath() {
        return scannedMethodsFilePath;
    }

    public static String getCfRulesFilePath() {
        return cfRulesFilePath;
    }

    public static String getGroundTruthGraphFilePath() {
        return groundTruthGraphFilePath;
    }

    public static String getPredictionGraphFilePath() {
        return predictionGraphFilePath;
    }


    public static void setGroundTruthDbPath(String groundTruthDbPath) {
        PathManager.groundTruthDbPath = groundTruthDbPath;
    }

    public static void setSideChannelDbPath(String sideChannelDbPath) {
        PathManager.sideChannelDbPath = sideChannelDbPath;
    }

    public static void setLogPath(String logPath) {
        PathManager.logPath = logPath;
    }

    public static void setOatFilePath(String oatFilePath) {
        PathManager.oatFilePath = oatFilePath;
    }

    public static void setToolConfigFilePath(String toolConfigFilePath) {
        PathManager.toolConfigFilePath = toolConfigFilePath;
    }

    public static void setConfigFilePath(String configFilePath) {
        PathManager.configFilePath = configFilePath;
    }

}
