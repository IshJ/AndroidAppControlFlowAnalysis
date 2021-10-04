package util;

import java.io.File;

public class PathManager {
    static  String rootFolderPath = System.getProperty("user.dir")
            .replace("tools" + File.separator + "AndroidCFI", "")
            .replace("scripts", "");;

    static  String dbFolderPath = rootFolderPath + "db" + File.separator;
    static String graphFolderPath = rootFolderPath + "graphs" + File.separator;
    static String metricsFolderPath = rootFolderPath + "metrics" + File.separator;
    static String scriptFolderPath = rootFolderPath + "scripts" + File.separator;
    static String configFolderPath = rootFolderPath + "config" + File.separator;

    static  String groundTruthDbPath = dbFolderPath + "ground_truth_full.out";
    static  String sideChannelDbPath = dbFolderPath + "side_channel_info_full.out";
    static  String filteredOutputPath = dbFolderPath + "filtered.out";
    static  String processedRecordsPath = dbFolderPath + "processedRecords.out";

    static  String logPath = rootFolderPath + "log.out";
    static  String oatFilePath = rootFolderPath + "oatdump.out";

    static String AddressStatsOut = metricsFolderPath + "addressStats.out";
    static  String toolConfigFilePath = configFolderPath + "toolConfig.out";
    static  String configFilePath = configFolderPath + "config.out";

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

    public static String getLogPath() {
        return logPath;
    }

    public static String getOatFilePath() {
        return oatFilePath;
    }

    public static String getAddressStatsOut() {
        return AddressStatsOut;
    }

    public static String getToolConfigFilePath() {
        return toolConfigFilePath;
    }

    public static String getConfigFilePath() {
        return configFilePath;
    }
}
