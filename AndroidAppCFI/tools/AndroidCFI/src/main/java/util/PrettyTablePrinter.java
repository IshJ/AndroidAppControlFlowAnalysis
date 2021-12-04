package util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PrettyTablePrinter {
    public static List<String> printPrettyTable(List<String> headers, List<String> rows, String columnSeparator) {
        List<String> prettyRows = new ArrayList<>();

        List<Integer> longestWordLengths = IntStream.range(0, headers.size())
                .mapToObj(i -> Integer.max(headers.get(i).length(), rows.stream().mapToInt(r -> r.split(columnSeparator)[i].length()).max().getAsInt()))
                .collect(Collectors.toList());

        StringBuilder headerBuilder = new StringBuilder().append("|");
        StringBuilder rowFormatBuilder = new StringBuilder().append("|");
        StringBuilder borderBuilder = new StringBuilder().append("+");

        for (int i = 0; i < longestWordLengths.size(); i++) {
            borderBuilder.append(String.format(" %" + (longestWordLengths.get(i) + 2) + "d+", 8).replace(" ", "-").replace("8", ""));
            headerBuilder.append(String.format(" " + headers.get(i) + "%" + (longestWordLengths.get(i) + 2 - headers.get(i).length()) + "d|", 8).replace("8", ""));
            rowFormatBuilder.append(" %-").append(longestWordLengths.get(i)).append("s |");
        }

        System.out.println(borderBuilder);
        prettyRows.add(borderBuilder.toString());

        System.out.println(headerBuilder);
        prettyRows.add(headerBuilder.toString());

        System.out.println(borderBuilder);
        prettyRows.add(borderBuilder.toString());


        for (int i = 0; i < rows.size(); i++) {
            String prettyRow = String.format(rowFormatBuilder.toString(), rows.get(i).split(columnSeparator));
            System.out.format(prettyRow);
            prettyRows.add(prettyRow);

            System.out.println();


        }
        System.out.println(borderBuilder);
        prettyRows.add(borderBuilder.toString());

        return prettyRows;

    }

    public static void main(String[] args) {
        List<String> headers = Arrays.asList("Column name", "ID", "Name");
        List<String> rows = Arrays.asList("c1yy#45#9", "c1fgfyy#4#90", "c1ggxzddsdsdwgfgdfgggfdgdgyy#4f5#93");
        printPrettyTable(headers, rows, "#");
    }
}