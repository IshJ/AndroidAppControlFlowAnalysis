package util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class TimeUtil {
    static long countToMs = 0;

    public static void calculateConverter(List<String> sideChannelLines){
        List<Long> timeList = sideChannelLines.stream().sequential().map(s->s.split("\\|")[0]).map(Long::valueOf).collect(Collectors.toList());
//        sideChannelLines.stream().sequential().map(s->s.split("\\|")).collect(Collectors.toMap(s->Long.valueOf(s[0]), s->Long.valueOf(s[3]));
        List<Long> counterList = sideChannelLines.stream().sequential().map(s->s.split("\\|")[3]).map(Long::valueOf).collect(Collectors.toList());
        List<Long> estimates = new ArrayList<>();
        Long prevTimeVal = timeList.get(0);
        Long prevCounterVal = counterList.get(0);
        for(int i=1;i<timeList.size();i++){
            Long curTimeVal = timeList.get(i);
            if(!curTimeVal.equals(prevTimeVal)){
                Long curCounterVal = counterList.get(i);
                if(curCounterVal.equals(prevCounterVal)){
                    continue;
                }
                else {
                    estimates.add((curCounterVal-prevCounterVal)/(curTimeVal-prevTimeVal));
                    prevTimeVal = curTimeVal;
                    prevCounterVal = curCounterVal;
                }

            }

        }
        countToMs = estimates.stream().sorted().collect(Collectors.toList()).get(estimates.size()/2);
    }

    public static long getTimeInMs(long countVal){
        if(countToMs==0){
            System.out.println("Please calculate the converting value first!");
            return -1;
        }
        return countVal/countToMs;
    }
}
