package com.jamasoftware.relationshipCreator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ItemMapper {
    private Config config;

    public ItemMapper(Config config) {
        this.config = config;
    }

    private static final Logger logger = Logger.getLogger("RelationshipImporter");
    public HashMap<String, ArrayList<Integer>> getMapping(String baseURL,
                                                          int projectId,
                                                          String fieldName,
                                                          int itemTypeId,
                                                          HashMap<String, ArrayList<Integer>> addTo) {
        Long totalResults;
        Long resultCount = -1L;
        Long startIndex = 0L;
        int maxResults = 50;

        JSONParser parser = new JSONParser();
        HashMap<String, ArrayList<Integer>> mapping;

        if (addTo == null) {
            mapping = new HashMap<String, ArrayList<Integer>>();
        } else {
            mapping = addTo;
        }

        while (resultCount != 0) {
            ProgressRecord.startBatch();
            String url = baseURL + "abstractitems?itemType=" +
                    itemTypeId + "&maxResults=" +
                    maxResults + "&project=" +
                    projectId + "&startAt=" +
                    startIndex;
            Response response = RestClient.get(url,
                    config.getCredentials(),
                    config.getDelay());
            if(response.getStatusCode() >= 400) {
                logger.log(Level.WARNING, "Server responded with " + response.getStatusCode() + " while retrieving items:" +
                                          "\n\tMapping may be incomplete.");
                return addTo;
            }

            try {
                JSONObject obj = (JSONObject) parser.parse(response.getResponse());
                JSONObject meta = (JSONObject) obj.get("meta");
                JSONObject pageInfo = (JSONObject) meta.get("pageInfo");
                resultCount = (Long) pageInfo.get("resultCount");
                totalResults = (Long) pageInfo.get("totalResults");
                startIndex += maxResults;

                for (Object o : (JSONArray) obj.get("data")) {
                    JSONObject item = (JSONObject) o;
                    JSONObject fields = (JSONObject) item.get("fields");
                    String field = (String) fields.get(fieldName);
                    if(field == null) {
                        field = (String) fields.get(fieldName + "$" + itemTypeId);
                    }
                    ArrayList<Integer> ids = mapping.get(field);
                    if (field != null) {
                        if (ids == null) {
                            ids = new ArrayList<Integer>();
                            mapping.put(field, ids);
                        }
                        Long longID = (Long)item.get("id");
                        ids.add(longID.intValue());
                    }
                }
                Long currentResult = startIndex - maxResults;
                ProgressRecord.print(ProgressRecord.batchAverage(resultCount.intValue(),
                        currentResult.intValue(),
                        totalResults.intValue()));
            } catch (ParseException e) {
                System.out.println("Server response is in unexpected format:\n" + response.getResponse() + "\n\n" + "Check baseURL and try again.");
                System.exit(1);
            }
        }
        return mapping;
    }
}
