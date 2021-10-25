package resultanalyser;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import util.PathManager;


public final class ChartWorker {

    private static final String S = "";
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel label = new JLabel(S, JLabel.CENTER);
    private final XYSeries series = new XYSeries("Result");
    private final XYSeriesCollection dataset = new XYSeriesCollection();

    private static boolean fromFile = false;
    private static String fileName;

    private void create(int minRange, int maxRange,
                        Map<Integer, Map<Integer, Integer>> timings, Map<Integer, Map<Integer, Integer>> boundaries, List<JavaMethod> analysedMethods) {
        JFrame f = new JFrame("");

        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(progressBar, BorderLayout.NORTH);
        JFreeChart chart = ChartFactory.createScatterPlot(
                "Groundtruths vs Side channel predictions", "time", "", dataset,
                PlotOrientation.VERTICAL, false, true, false);
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.getRangeAxis().setRange(minRange, maxRange);
        plot.setBackgroundPaint(Color.BLACK);
        plot.getDomainAxis().setStandardTickUnits(
                NumberAxis.createIntegerTickUnits());
        plot.getRangeAxis().setVisible(false);
        XYLineAndShapeRenderer renderer
                = (XYLineAndShapeRenderer) plot.getRenderer();
//        renderer.setSeriesShapesVisible(0, true);
        f.add(new ChartPanel(chart) {
//            @Override
//            public Dimension getPreferredSize() {
//                return new Dimension(640, 480);
//            }
        }, BorderLayout.CENTER);
        f.add(label, BorderLayout.SOUTH);
        f.pack();
        f.setLocationRelativeTo(null);

        f.setExtendedState(JFrame.MAXIMIZED_BOTH);
//        f.setUndecorated(true);

        f.setVisible(true);


        runCalc(timings, boundaries, analysedMethods);
    }

    private void runCalc(Map<Integer, Map<Integer, Integer>> timings, Map<Integer, Map<Integer, Integer>> boundaries, List<JavaMethod> analysedMethods) {
        progressBar.setIndeterminate(false);
        TwoWorker task = new TwoWorker(timings, boundaries, analysedMethods);
        task.formatData();
        task.addPropertyChangeListener((PropertyChangeEvent e) -> {
            if ("progress".equals(e.getPropertyName())) {
                progressBar.setIndeterminate(false);
                progressBar.setValue((Integer) e.getNewValue());
            }
        });
        task.execute();
    }

    private class TwoWorker extends SwingWorker<Double, Double> {

        private static final int N = 5;
        private final DecimalFormat df = new DecimalFormat(S);
        private Map<Integer, Map<Integer, Integer>> timings;
        private Map<Integer, Map<Integer, Integer>> boundaries;
        private List<JavaMethod> analysedMethods;

        List<List<Integer>> boundaryTimeList = new ArrayList<>();
        List<List<Integer>> timingTimeList = new ArrayList<>();
        List<String> seriesNames = new ArrayList<>();
        List<XYSeries> seriesList = new ArrayList<>();
        List<Integer> allTimes = new ArrayList<>();


        double x = 1;
        private int n;
        int mC = 0;

        public TwoWorker() {
        }

        public TwoWorker(Map<Integer, Map<Integer, Integer>> timings, Map<Integer, Map<Integer, Integer>> boundaries, List<JavaMethod> analysedMethods) {
            this.timings = timings;
            this.boundaries = boundaries;
            this.analysedMethods = analysedMethods;
        }

        public void formatData() {
            if (!fromFile) {
                seriesNames = analysedMethods.stream().filter(JavaMethod::isActive).map(m -> m.getOdexOffsets().get(0)).collect(Collectors.toList());
                seriesList = IntStream.range(0, boundaries.size()).mapToObj(i -> new XYSeries("m_" + seriesNames.get(i))).collect(Collectors.toList());
                seriesList.forEach(dataset::addSeries);
                int nX = boundaries.size();
                mC = nX;

                for (int i = 0; i < nX; i++) {
                    boundaryTimeList.add(new ArrayList<>(boundaries.get(i).keySet()));
                    timingTimeList.add(new ArrayList<>(timings.get(i).keySet()));
                    allTimes.addAll(boundaries.get(i).keySet());
                    allTimes.addAll(timings.get(i).keySet());
                    allTimes = allTimes.stream().distinct().collect(Collectors.toList());
                }
                allTimes.sort(Integer::compare);
            } else {
                try {
                    List<String> records = Files.lines(Paths.get(fileName), StandardCharsets.ISO_8859_1).collect(Collectors.toList());
                    seriesNames = Arrays.asList(records.get(0).split(":")[1].split(","));
                    mC = seriesNames.size();

                    seriesList = IntStream.range(0, mC).mapToObj(i -> new XYSeries("m_" + seriesNames.get(i))).collect(Collectors.toList());
                    seriesList.forEach(dataset::addSeries);


                    for (int i = 0; i < seriesNames.size(); i++) {
                        int finalI = i;
                        List<Integer> timingLineTimes = Arrays.stream(
                                records.stream()
                                        .filter(r -> r.contains("timings_" + finalI)).findFirst().get().split(":")[1].split(","))
                                .map(Integer::valueOf).collect(Collectors.toList());


                        List<Integer> boundaryLineTimes = Arrays.stream(
                                records.stream().filter(r -> r.contains("boundaries_" + finalI)).findFirst().get().split(":")[1].split(","))
                                .map(Integer::valueOf).collect(Collectors.toList());

                        boundaryTimeList.add(boundaryLineTimes);
                        timingTimeList.add(timingLineTimes);
                        allTimes.addAll(boundaryLineTimes);
                        allTimes.addAll(timingLineTimes);
                        allTimes = allTimes.stream().distinct().collect(Collectors.toList());
                    }
                    allTimes.sort(Integer::compare);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        @Override
        protected Double doInBackground() throws Exception {
            for (int i = 0; i < allTimes.size(); i++) {
                int t = allTimes.get(i);
                setProgress(t * (100 / allTimes.get(allTimes.size() - 1)));
                IntStream.range(0, mC).filter(m -> boundaryTimeList.get(m).contains(t)).forEach(mId -> seriesList.get(mId).add(t, 0+mId*0.1));
                IntStream.range(0, mC).filter(m -> timingTimeList.get(m).contains(t))
                        .forEach(mId -> seriesList.get(mId).add(t, 1.0 + mId * 0.1));
                Thread.sleep(300);
            }
            return 0d;
        }
    }

    public static void createTimeLine(Map<Integer, Map<Integer, Integer>> timings, Map<Integer, Map<Integer, Integer>> boundaries,
                                      List<JavaMethod> analysedMethods) {
        EventQueue.invokeLater(() -> new ChartWorker().create(-1, 2, timings, boundaries, analysedMethods));

    }

    public static void main(String[] args) {

        fromFile = true;
        fileName = args.length==0?PathManager.getChartWorkerRecordsPath():args[0]+"/chartWorkerRecords.out";
        System.out.println("fileName:"+fileName);
        ChartWorker.createTimeLine(new HashMap<>(), new HashMap<>(), new ArrayList<>());

    }


}