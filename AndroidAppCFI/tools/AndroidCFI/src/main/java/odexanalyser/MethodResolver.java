package odexanalyser;

import util.PathManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MethodResolver {

    public static boolean isScannedMethod(String methodName, List<String> packageNames) {
//        ToDo: write to compare parameters in case of method overwrites
//        ignoring the return parameters
        methodName = methodName.split("\\(")[0].split(" ")[2].split("_aroundBody")[0];
        String finalMethodName = methodName;

        return packageNames.stream().anyMatch(finalMethodName::contains);
    }


}
