package odexanalyser;

import util.PathManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OdexStatPrinter {
    public static void main(String[] args) throws IOException {
        List<String> lines = Files.lines(Paths.get(PathManager.getOatFilePath()), StandardCharsets.ISO_8859_1)
                .collect(Collectors.toList());
        List<String> methodStats = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!isMethodBegin(lines.get(i))) {
                continue;
            }
            String line = lines.get(i);
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
            String methodName = lines.get(methodBeginId).strip();
            while (!isNativeBegin(lines.get(methodBeginId)) && methodBeginId < methodEndLineId) {
                methodBeginId++;
            }
            int methodWidth = methodEndLineId - methodBeginId;
            methodStats.add(methodName.split(":")[1].strip()+": " + methodWidth);

        }

        Files.write(Path.of(PathManager.getMethodStatsOut()), methodStats, StandardCharsets.ISO_8859_1);

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

}
