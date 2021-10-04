package util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigManager {

    public static Map<String, String> readConfigs(String configFilePath) throws IOException {
        return Files.lines(Paths.get(configFilePath), StandardCharsets.ISO_8859_1).collect(Collectors.toList())
                .stream().filter(l -> !l.contains("//") && l.contains(":"))
                .collect(Collectors.toMap(s->s.split(":")[0], s-> s.split(":")[1]));
    }

    public static boolean insertConfig(String configFilePath, String key, String value){
        boolean isSuccess = false;

        try {
            List<String> toolConfigLines = Files.lines(Paths.get(configFilePath), StandardCharsets.ISO_8859_1).collect(Collectors.toList());
            for (int i = 0; i < toolConfigLines.size(); i++) {
                if (toolConfigLines.get(i).contains(key)) {
                    String configLine = toolConfigLines.get(i);
                    String oldVal = configLine.split(":")[1];
                    isSuccess = true;
                    if (!oldVal.equals(value)) {
                        configLine = configLine.replace(oldVal, value);
                        toolConfigLines.set(i, configLine);
                        Files.write(Path.of(configFilePath), toolConfigLines, StandardCharsets.ISO_8859_1);
                    }
                    break;
                }
            }
            if (!isSuccess) {
                toolConfigLines.add(key + ":" + value);
                Files.write(Path.of(configFilePath), toolConfigLines, StandardCharsets.ISO_8859_1);
//                System.out.println("ToolConfigs updated-> " + key + ":" + value);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return isSuccess;
    }
}
