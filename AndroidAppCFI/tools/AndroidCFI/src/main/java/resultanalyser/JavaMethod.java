package resultanalyser;

import java.util.ArrayList;
import java.util.List;

public class JavaMethod {
    private String odexName;
    private String appName;
    private int odexMethodId;
    private int appMethodId;
    private int LogParserId;
    private List<String> odexOffsets = new ArrayList<>();
    private List<String> appOffsets= new ArrayList<>();
    private boolean isActive = false;
    private int size=0;
    private List<BoundaryTime> boundaryTimes = new ArrayList<>();
    private List<Integer> hitTimes = new ArrayList<>();




    public JavaMethod() {
    }

    public JavaMethod(String odexName, int odexMethodId, List<String> odexOffsets, List<String> appOffsets ) {
        this.odexName = odexName;
        this.odexMethodId = odexMethodId;
        this.odexOffsets.addAll(odexOffsets);
        this.appOffsets.addAll(appOffsets);

    }

    public String getOdexName() {
        return odexName;
    }

    public void setOdexName(String odexName) {
        this.odexName = odexName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public int getOdexMethodId() {
        return odexMethodId;
    }

    public void setOdexMethodId(int odexMethodId) {
        this.odexMethodId = odexMethodId;
    }

    public int getAppMethodId() {
        return appMethodId;
    }

    public void setAppMethodId(int appMethodId) {
        this.appMethodId = appMethodId;
    }


    public List<String> getOdexOffsets() {
        return odexOffsets;
    }

    public void setOdexOffsets(List<String> odexOffsets) {
        this.odexOffsets = odexOffsets;
    }

    public void addOdexOffset(String odexOffset) {
        this.odexOffsets.add(odexOffset);
    }


    public List<String> getAppOffsets() {
        return appOffsets;
    }

    public void setAppOffsets(List<String> appOffsets) {
        this.appOffsets = appOffsets;
    }

    public void addAppOffset(String appOffset) {
        this.appOffsets.add(appOffset);
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getLogParserId() {
        return LogParserId;
    }

    public void setLogParserId(int logParserId) {
        LogParserId = logParserId;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getShortName(){
        String[] splits = this.getAppName().split("\\(")[0].split("\\.");
        return splits[splits.length-1];

    }

    public void addBoundaryTime(int startTime, int endTime){
        boundaryTimes.add(new BoundaryTime(startTime, endTime));
    }

    public void addHitTime(int hitTime){
        hitTimes.add(hitTime);
    }


}

class BoundaryTime{
    int startTime;
    int endTime;

    public BoundaryTime(int startTime, int endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getEndTime() {
        return endTime;
    }
}
