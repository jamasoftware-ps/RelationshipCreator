package com.jamasoftware.relationshipCreator;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RelationshipCreator {
	private static final Logger logger = Logger.getLogger("RelationshipImporter");
    private JSONParser parser = new JSONParser();
	private Config config;
	private int passNumber = 0;
    private int createdRelationships = 0;

	public RelationshipCreator(Config config) {
		this.config = config;
	}

	public void createRelationships(LinkedList<String[]> csv, int attempts) {
		if (attempts == 0) return;
		System.out.println("Beginning pass " + ++passNumber + " of " + config.getRetries() + "                            ");
        int totalRecords = csv.size();
		ProgressRecord.start(csv.size());
		for (int i = 0, j = 0; i < csv.size(); ++i) {
			String[] row = csv.get(i);
            StringBuilder payload = new StringBuilder(200);
            payload.append("{\"fromItem\":");
            payload.append(row[0]);
            payload.append(",");
            payload.append("\"toItem\":");
            payload.append(row[1]);
			if (row[2] != null) {
				payload.append(",\"relationshipType\":");
                payload.append(row[2]);
			}
			payload.append("}");
			Response response = RestClient.post(config, config.getBaseURL() +"v1/"+ "relationships/",
					payload.toString(),
					config.getCredentials(),
                    config.getDelay());
			if (response.getStatusCode() < 400) {
                ProgressRecord.setCompletedRecords(++createdRelationships);
				String[] array = response.getResponse().split("/");
				String newRelationship = array[array.length - 1].split("\"")[0];
				logger.log(Level.INFO, newRelationship);
				csv.remove(i);
				--i;
			} else if (attempts == 1) {
                try {
                    JSONObject res = (JSONObject) parser.parse(response.getResponse());
                    JSONObject meta = (JSONObject)res.get("meta");
                    String message = (String)meta.get("message");
                    logger.log(Level.SEVERE, message);
                } catch (ParseException e) {
					logger.log(Level.SEVERE, "Unable to retrieve error message.");
                }
            }
            ProgressRecord.mark();
            if(j++ % 5 == 0) {
                ProgressRecord.print(ProgressRecord.average() + "   Created " + createdRelationships + " out of " + totalRecords + " relationships.");
            }
		}
		if (csv.size() > 0) {
			createRelationships(csv, attempts - 1);
		}
	}
}
