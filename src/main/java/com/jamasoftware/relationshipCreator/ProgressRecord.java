package com.jamasoftware.relationshipCreator;

public class ProgressRecord {
    private static int timesToAverage = 30;
    private static long[] timeList = new long[timesToAverage];
    private static int timeCount = 0;
    private static long previousTime = 0;
    private static int remainingTimes = 0;
    private static long previousBatchTime = 0;
    private static int totalRecords;
    private static int completedRecords;
    private ProgressRecord() {}


    public static void startBatch() {
        previousBatchTime = System.currentTimeMillis();
    }

    public static String batchAverage(int resultCount,
                                      int startIndex,
                                      int totalResults) {
        if(resultCount == 0) {
            return "";
        }
        if(previousBatchTime == 0) {
            previousBatchTime = System.currentTimeMillis();
            return "Calculating remaining time.";
        }
        int remaining = totalResults - startIndex;
        long avgMillis = (System.currentTimeMillis() - previousBatchTime) / resultCount;
        String estimate = "Estimated time remaining: ";

        if(avgMillis  * remaining < 60000) {
            return estimate + "less than 1 minute";
        } else {
            return estimate + ((avgMillis * remaining) / 60000) + " minutes";
        }
    }

    public static void start(int total) { reset(total); }

    public static void reset(int total) {
        remainingTimes = total;
        timeList = new long[timesToAverage];
        timeCount = 0;
    }

    public static void mark() {
        --remainingTimes;
        if(previousTime == 0) {
            previousTime = System.currentTimeMillis();
        }
        timeList[timeCount++ % timesToAverage] = System.currentTimeMillis() - previousTime;
        previousTime = System.currentTimeMillis();
    }

    public static String average() {
        long totalTime = 0;
        long averageTime;
        long remainingTime;
        if(timeCount < timesToAverage) {
            return "Calculating remaining time.";
        }
        for(int i = 0; i < timesToAverage; ++i) {
            totalTime += timeList[i];
        }
        averageTime = totalTime / timesToAverage;
        remainingTime = averageTime * remainingTimes;
        String time = "Estimated time remaining: ";
        time += String.format("%02d", remainingTime / (60* 60 * 1000));
        time += ":";
        time += String.format("%02d", (remainingTime / (60 * 1000)) % 60);
        time += ":";
        time += String.format("%02d", (remainingTime / 1000) % 60);
        return time;
    }

    public static String threadedAverage(int completedRecords, long startTime, int totalRecords) {
        if(completedRecords == 0) return "Calculating remaining time.";
        long remainingTime = ((System.currentTimeMillis() - startTime)/completedRecords) * (totalRecords - completedRecords);
        String time = "Estimated time remaining: ";
        time += String.format("%02d", remainingTime / (60* 60 * 1000));
        time += ":";
        time += String.format("%02d", (remainingTime / (60 * 1000)) % 60);
        time += ":";
        time += String.format("%02d", (remainingTime / 1000) % 60);
        return time;
    }

    public static void print(String toPrint) {
        System.out.print(toPrint + "         \r");
    }

    public static void setTotalRecords(int total) {
        totalRecords = total;
    }

    public static void setCompletedRecords(int completed) {
        completedRecords = completed;
    }

    public static String finishCreating() {
        return "Successfully created " + completedRecords +
                " out of " +
                totalRecords +
                " relationships.";
    }

    public static String finishDeleting() {
        return "Successfully deleted " + completedRecords +
                " out of " +
                totalRecords +
                " relationships.";
    }
}
