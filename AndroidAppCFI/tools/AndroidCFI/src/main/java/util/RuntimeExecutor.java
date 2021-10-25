package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class RuntimeExecutor {
    public static void runCommand(String cmd, boolean needOutput) throws IOException {
        Process p = Runtime.getRuntime().exec(cmd);
        if (needOutput) {
            InputStream stdout = p.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("stdout: " + line);
            }
        }
    }

    public static void showImage(String imageName) throws IOException {
        Runtime.getRuntime().exec("eog " + imageName);

    }
}
