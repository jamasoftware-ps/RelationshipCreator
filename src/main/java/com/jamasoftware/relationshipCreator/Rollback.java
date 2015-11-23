package com.jamasoftware.relationshipCreator;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;

public class Rollback {
    private JSONParser parser = new JSONParser();
    private int passNumber = 0;
    private int deletedRelationships = 0;
    private Config config;

    public Rollback(Config config) {
        this.config = config;
    }

    public static LinkedList<String> buildRollbackList() {
        BufferedReader br;
        String line;
        LinkedList<String> list = new LinkedList<>();

        try {
            br = new BufferedReader(new FileReader("rollback.log"));
            while((line = br.readLine()) != null) {
                if(line.startsWith("NEW")) {
                    String[] row = line.split(":");
                    list.add(row[1].trim());
                }
            }
            return list;
        } catch (IOException e) {
            return null;
        }
    }

    public void deleteRelationships(LinkedList<String> list, int attempts) {
		if (attempts == 0) return;
		System.out.println("Beginning rollback pass " + ++passNumber + " of " + config.getRetries() + "                            ");
        int totalRecords = list.size();
		for (int i = 0, j = 0; i < list.size(); ++i) {
			String row = list.get(i);
            String url = config.getBaseURL() + "relationships/" + row;
			Response response = RestClient.delete(url,
					config.getCredentials(),
                    config.getDelay());
			if (response.getStatusCode() < 400) {
                ProgressRecord.setCompletedRecords(++deletedRelationships);
				list.remove(i);
				--i;
			} else if (attempts == 1 && !response.getResponse().equals("")) {
                try {
                    JSONObject res = (JSONObject) parser.parse(response.getResponse());
                    JSONObject meta = (JSONObject)res.get("meta");
                    System.out.println(meta.get("message") + "           ");
                } catch (ParseException e) {
                    System.out.println(e.toString() + "  Unable to parse response from server.");
                }
            }
            ProgressRecord.mark();
            if(j++ % 5 == 0) {
                ProgressRecord.print(ProgressRecord.average() + "   Deleted " + deletedRelationships + " out of " + totalRecords + " relationships.");
            }
		}
		if (list.size() > 0) {
			deleteRelationships(list, attempts - 1);
		}
	}
}
