package com.jamasoftware.relationshipCreator;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger("RelationshipImporter");
    private static int threadCount = 15;
    private static long threadPollingInterval = 5000;

    public static void main(String[] args) {
        try {
            Config config = new Config();
            attemptRollback(config);

            Date date = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy_h-mm-ss-a");
            String formattedDate = sdf.format(date);
            Handler fileHandler = new FileHandler("importLog" + formattedDate + ".log");
            logger.setUseParentHandlers(false);
            fileHandler.setFormatter(new TerseFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);

            createRelationships(config);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            String end = ProgressRecord.finishCreating();
            System.out.println(end);
            logger.log(Level.OFF, end);
        }
    }

    public static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean allThreadsFinished(Thread[] threads) {
        for(Thread t : threads) {
            if(t.isAlive()) {
                return false;
            }
        }
        return true;
    }

    public static void attemptRollback(Config config) {
        LinkedList<String> list = Rollback.buildRollbackList();
        if(list == null) return;
        ProgressRecord.setTotalRecords(list.size());
        ProgressRecord.start(list.size());
        if (config.getDelay() != 0) {
            doRollback(config, list);
        } else {
            doRollbackThreaded(config, list);
        }
        System.out.println(ProgressRecord.finishDeleting());
        System.exit(0);
    }

    public static void doRollback(Config config, LinkedList<String> list) {
        Rollback rollback = new Rollback(config);
        rollback.deleteRelationships(list, config.getRetries());
    }

    public static void doRollbackThreaded(Config config, LinkedList<String> list) {
            if(list == null) return;
            LinkedList<LinkedList<String>> subLists = new LinkedList<LinkedList<String>>();
            RollbackThreaded[] runnables = new RollbackThreaded[threadCount];
            Thread[] threads = new Thread[threadCount];
            AtomicInteger progress = new AtomicInteger(0);

            for(int i = 0; i < threadCount; ++i) {
                subLists.add(new LinkedList<String>());
            }

            for(int i = 0; i < list.size(); ++i) {
                subLists.get(i % threadCount).add(list.get(i));
            }

            for(int i = 0; i < threadCount; ++i) {
                runnables[i] = new RollbackThreaded(config, subLists.get(i), progress);
                threads[i] = new Thread(runnables[i]);
                threads[i].start();
            }

            System.out.println("Deleting relationships.                      ");

            int totalRecords = list.size();
            long startTime = System.currentTimeMillis();
            while(!allThreadsFinished(threads)) {
                pause(threadPollingInterval);
                ProgressRecord.print(ProgressRecord.threadedAverage(progress.get(), startTime, totalRecords) + "   Deleted " + progress.get() + " out of " + totalRecords + " relationships.");
            }
            ProgressRecord.setCompletedRecords(progress.get());
    }

    public static void createRelationships(Config config) throws IOException {
        RelationshipCreator relationshipCreator = new RelationshipCreator(config);
        RelationshipMapper relationshipMapper = new RelationshipMapper(config);
        ItemMapper itemMapper = new ItemMapper(config);

        HashMap<String, ArrayList<Integer>> colAMap = null;
        HashMap<String, ArrayList<Integer>> colBMap = null;

        int projects = config.getColumnAProjects().length + config.getColumnBProjects().length;
        int project = 0;

        for (int projectID : config.getColumnAProjects()) {
            System.out.println("Creating field and itemType map " + ++project + " of " + projects);
            colAMap = itemMapper.getMapping(config.getBaseURL(),
                    projectID,
                    config.getColumnAFieldName(),
                    config.getColumnAItemType(),
                    colAMap);
            if (colAMap == null) {
                String error = "No project with ID " + projectID + " found.\r\n\tAborting";
                logger.log(Level.SEVERE, error);
                System.out.println(error);
                System.exit(1);
            }
        }
        for (int projectID : config.getColumnBProjects()) {
            System.out.println("Creating field and itemType map " + ++project + " of " + projects + "           ");
            colBMap = itemMapper.getMapping(config.getBaseURL(),
                    projectID,
                    config.getColumnBFieldName(),
                    config.getColumnBItemType(),
                    colBMap);
            if (colBMap == null) {
                String error = "No project with ID " + projectID + " found.\r\n\tAborting";
                logger.log(Level.SEVERE, error);
                System.out.println(error);
                System.exit(1);
            }
        }
        LinkedList<String[]> list = relationshipMapper.createMapping(colAMap, colBMap);

        System.out.println("Creating relationships.                      ");
        if (config.getDelay() != 0) {
            relationshipCreator.createRelationships(list, config.getRetries());
        } else {
            createRelationshipsThreaded(config, list);
        }
    }
    public static void createRelationshipsThreaded(Config config, LinkedList<String[]> list) {
        LinkedList<LinkedList<String[]>> subLists = new LinkedList<LinkedList<String[]>>();
        RelationshipCreatorThreaded[] runnables = new RelationshipCreatorThreaded[threadCount];
        Thread[] threads = new Thread[threadCount];
        AtomicInteger progress = new AtomicInteger(0);

        for(int i = 0; i < threadCount; ++i) {
            subLists.add(new LinkedList<String[]>());
        }

        for(int i = 0; i < list.size(); ++i) {
            subLists.get(i % threadCount).add(list.get(i));
        }

        for(int i = 0; i < threadCount; ++i) {
            runnables[i] = new RelationshipCreatorThreaded(config, subLists.get(i), progress);
            threads[i] = new Thread(runnables[i]);
            threads[i].start();
        }

        int totalRecords = list.size();
        long startTime = System.currentTimeMillis();
        while(!allThreadsFinished(threads)) {
            pause(threadPollingInterval);
            ProgressRecord.print(ProgressRecord.threadedAverage(progress.get(), startTime, totalRecords) + "   Created " + progress.get() + " out of " + totalRecords + " relationships.");
        }
        ProgressRecord.setCompletedRecords(progress.get());
    }
}
