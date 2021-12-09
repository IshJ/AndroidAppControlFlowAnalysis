package logreader;

import util.ConfigManager;
import util.PathManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class AdbLogReader {


    static final int FILTER_THRESHOLD = 1000;
    static final int ADJUSTED_FILTER_THRESHOLD = 100;
    static Map<String, String> configMap = new HashMap<>();


    public static void main(String[] args) throws IOException, InterruptedException {

        configMap = ConfigManager.readConfigs(PathManager.getConfigFilePath());

        if (args.length > 0) {
            execCmd("adb logcat | grep \"odex copied\"");
        } else {
            int ot = execCmdForLoop("adb logcat | grep \"#4_0_\"");
            System.out.println("+++");
            System.exit(ot);
        }
    }

    public static int execCmd(String cmd) throws IOException {
        Scanner s = new Scanner(Runtime.getRuntime().exec(cmd).getInputStream());
        String ot = s.nextLine();
        while (s.hasNextLine()) {
            if (ot.contains("odex copied")) {
                return 1;
            }
            ot = s.hasNextLine() ? s.nextLine() : "";

        }
        return -1;
    }

    public static int execCmdForLoop(String cmd) throws IOException, InterruptedException {
        int count = 0;
        boolean isThresholdChecked = false;
        Scanner s = new Scanner(Runtime.getRuntime().exec(cmd).getInputStream());
        String ot = s.nextLine();
        if (isThresholdChecked) {
            ConfigManager.insertConfig(PathManager.getToolConfigFilePath(), "parseLogs", "1");
        }
        while (true) {

            if (!isThresholdChecked && ot.contains("adjust_threshold1 final threshold")) {
                String threshold = ot.split(" threshold")[1].strip();
                System.out.println("threshold: " + threshold);

                if (Integer.parseInt(threshold) < ADJUSTED_FILTER_THRESHOLD) {
                    System.out.println("Threshold is lower than the filter threshold " + ADJUSTED_FILTER_THRESHOLD + "; stopping the program");
                    ConfigManager.insertConfig(PathManager.getToolConfigFilePath(), "parseLogs", "0");
                    return -1;
                }
                ConfigManager.insertConfig(PathManager.getToolConfigFilePath(), "ceilVal", String.valueOf(Integer.parseInt(threshold.strip()) + 10));
                System.out.println("LogReader: Updated threshold (" + threshold + ") from Log");
                isThresholdChecked = true;
                ConfigManager.insertConfig(PathManager.getToolConfigFilePath(), "parseLogs", "1");
            }


            if (ot.contains("scanned offsets")) {
                String offsets = ot.split("offsets:")[1];
                ConfigManager.insertConfig(PathManager.getToolConfigFilePath(), "offsets", offsets);
                System.out.println("LogReader: Updated offsets from Log");
            }

            if (ot.contains("MethodMap")) {
                String methodMapName = "MethodMap" + ot.split("MethodMap")[1].split(":")[0];
                String methodMap = ot.split(methodMapName + ":")[1];
                ConfigManager.insertConfig(PathManager.getToolConfigFilePath(), methodMapName, methodMap);
                System.out.println("LogReader: Updated MethodMap from Log");
            }

            if (ot.contains("Automation_completed")) {
                Thread.sleep(10);
                if (isThresholdChecked) {
                    return 1;
                }
                System.out.println("LogReader: threshold wasn't logged");
                return -1;
            }
            ot = s.hasNextLine() ? s.nextLine() : "";
        }

    }


}
