package com.jamasoftware.relationshipCreator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RelationshipMapper {
    private static final Logger logger = Logger.getLogger("RelationshipImporter");
    private Config config;
    private HashMap<String, Integer> relationshipTypes;
    private LinkedList<String[]> csv;
    private String delimiterWithSpace;

    public RelationshipMapper(Config config) throws IOException {
        this.config = config;
        delimiterWithSpace = config.getDelimiter() + " ";
        relationshipTypes = getRelationshipTypes();
        csv = readCSV();
    }

    public HashMap<String, Integer> getRelationshipTypes() {
        HashMap<String, Integer> mapping = new HashMap<>();

        int resultCount = -1;
        int startIndex = 0;

        JSONParser parser = new JSONParser();

        while(resultCount != 0) {
            String url = config.getBaseURL() + "relationshiptypes?startAt=" + startIndex;
            Response response = RestClient.get(url, config.getCredentials(), config.getDelay());
            if(response.getStatusCode() > 400) {
                System.out.println("Received " + response.getStatusCode() + " while trying to retrieve relationship types.");
                System.out.println("Aborting.");
                System.exit(1);
            }

            try {
                JSONObject obj = (JSONObject) parser.parse(response.getResponse());
                JSONObject meta = (JSONObject) obj.get("meta");
                JSONObject pageInfo = (JSONObject) meta.get("pageInfo");
                if(pageInfo != null) {
                    resultCount = (int) (long) pageInfo.get("resultCount");
                    startIndex += 20;
                } else {
                    resultCount = 0;
                }

                for (Object o : (JSONArray) obj.get("data")) {
                    JSONObject item = (JSONObject) o;
                    mapping.put(((String) item.get("name")).toLowerCase(), (int) (long) item.get("id"));
                }
            } catch (ParseException|NullPointerException e) {
                System.out.println("Server response is in unexpected format:\n" + response.getResponse() + "\n\n" + "Check baseURL and try again.");
                System.exit(1);
            }
        }

        return mapping;
    }

    public void testRelationshipType(String[] row, StringBuilder errorDescription) {
        if(row[2] != null) {
            Integer relationshipType = relationshipTypes.get(row[2]);
            if (relationshipType == null) {
                errorDescription.append("Relationship type \"");
                errorDescription.append(row[2]);
                errorDescription.append("\" not found for row (");
                errorDescription.append(row[0]);
                errorDescription.append(delimiterWithSpace);
                errorDescription.append(row[1]);
                errorDescription.append(delimiterWithSpace);
                errorDescription.append(row[2]);
                errorDescription.append("):\n\tCreating default instead.");

                logger.log(Level.WARNING, errorDescription.toString());
                errorDescription.setLength(0);
                row[2] = null;
            }
        }
    }

    public boolean itemsAreFound(ArrayList<Integer> columnA, ArrayList<Integer> columnB, String[] row, StringBuilder errorDescription) {
        boolean pass = true;
        if (columnA == null || columnB == null) {
            pass = false;
            errorDescription.append("No match found for row (");
            errorDescription.append(row[0]);
            errorDescription.append(delimiterWithSpace);
            errorDescription.append(row[1]);
            if (row[2] != null) {
                errorDescription.append(delimiterWithSpace);
                errorDescription.append(row[2]);
            }
            errorDescription.append("):");
            if (columnA == null) {
                String errorCause = itemNotFound(row[0], config.getColumnAFieldName(), config.getColumnAItemType());
                logger.log(Level.SEVERE, errorDescription.toString() + errorCause);
            }
            if (columnB == null) {
                String errorCause = itemNotFound(row[1], config.getColumnBFieldName(), config.getColumnBItemType());
                logger.log(Level.SEVERE, errorDescription.toString() + errorCause);
            }
            errorDescription.setLength(0);
        }
        return pass;
    }

    public String itemNotFound(String rowValue, String fieldName, int itemType) {
        return "\n\t\"" +
                rowValue +
                "\" does not match any Jama item's \"" +
                fieldName +
                "\" field, where item type API ID is " +
                itemType;
    }

    public boolean itemsAreUnique(ArrayList<Integer> columnA, ArrayList<Integer> columnB, String[] row, StringBuilder errorDescription) {
        boolean pass = true;
        if (columnA.size() != 1 || columnB.size() != 1) {
            pass = false;
            errorDescription.append("Unable to create row: (");
            errorDescription.append(row[0]);
            errorDescription.append(delimiterWithSpace);
            errorDescription.append(row[1]);
            if (row[2] != null) {
                errorDescription.append(delimiterWithSpace);
                errorDescription.append(row[2]);
            }
            errorDescription.append("):");
            if (columnA.size() != 1) {
                errorDescription.append(itemsAreNotUnique(row[0], columnA.size()));
            }
            if (columnB.size() != 1) {
                errorDescription.append(itemsAreNotUnique(row[1], columnB.size()));
            }
            logger.log(Level.SEVERE, errorDescription.toString());
        }
        return pass;
    }

    public String itemsAreNotUnique(String rowValue, int matchCount) {
        return "\n\t" +
                rowValue +
                " matches " +
                matchCount +
                " Jama items.";
    }

    public LinkedList<String[]> createMapping(Map<String, ArrayList<Integer>> aColumn,
                                              Map<String, ArrayList<Integer>> bColumn) {
        ArrayList<Integer> columnA;
        ArrayList<Integer> columnB;

        for(String[] row : csv) {
            StringBuilder errorDescription = new StringBuilder(100);
            columnA = aColumn.get(row[0]);
            columnB = bColumn.get(row[1]);
            testRelationshipType(row, errorDescription);
            if (itemsAreFound(columnA, columnB, row, errorDescription) &&
            itemsAreUnique(columnA, columnB, row, errorDescription)) {
                row[0] = aColumn.get(row[0]).get(0).toString();
                row[1] = bColumn.get(row[1]).get(0).toString();
                if(row[2] != null) {
                    row[2] = relationshipTypes.get(row[2].toLowerCase()).toString();
                }
            }
        }

        for(int i = 0; i < csv.size(); ++i) {
            if(!csv.get(i)[0].matches("\\d+") || !csv.get(i)[1].matches("\\d+")) {
                csv.remove(i);
                --i;
            }
        }

        return csv;
    }

    public LinkedList<String[]> readCSV() throws IOException {
        BufferedReader br = null;
        String csvSplitBy = config.getDelimiter();
        String line = "";
        LinkedList<String[]> list = new LinkedList<>();
        int totalRows = 0;

        try {
            br = new BufferedReader(new FileReader(config.getCsvFile()));
            while((line = br.readLine()) != null) {
                ++totalRows;
                String[] row = new String[3];
                String[] splitLine = line.split(csvSplitBy);
                if (splitLine.length < 2 || splitLine.length > 3) {
                    String message = "Row (" + line + ") is malformed:\n\tSkipping";
                    logger.log(Level.SEVERE, message);
                    continue;
                }
                for(int i = 0; i < splitLine.length; ++i) {
                    row[i] = splitLine[i].trim();
                }
                list.add(row);
            }
            ProgressRecord.setTotalRecords(totalRows);
        } catch (IOException e) {
            System.out.println("Error opening " + config.getCsvFile());
            System.out.println("Aborting.");
            System.exit(1);
        } finally {
            if(br != null) {
                br.close();
            }
        }
        return list;
    }
}
