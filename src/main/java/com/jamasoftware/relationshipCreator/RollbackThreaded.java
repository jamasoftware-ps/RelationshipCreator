package com.jamasoftware.relationshipCreator;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

public class RollbackThreaded implements Runnable {
    private JSONParser parser = new JSONParser();
    private Config config;
    private LinkedList<String> list;
    private AtomicInteger progress;

    public RollbackThreaded(Config config, LinkedList<String> list, AtomicInteger progress) {
        this.list = list;
        this.config = config;
        this.progress = progress;
    }

    public void run() {
        deleteRelationships(list, config.getRetries());
    }

    public void deleteRelationships(LinkedList<String> list, int attempts) {
		if (attempts == 0) return;
        int j = 0;
		for (int i = 0; i < list.size(); ++i) {
			String row = list.get(i);
            StringBuilder url = new StringBuilder(200);
            url.append(config.getBaseURL());
            url.append("v1/" );
            url.append("relationships/");
            url.append(row);
			Response response = RestClient.delete(config, url.toString(),
					config.getCredentials(),
                    config.getDelay());
			if (response.getStatusCode() < 400) {
                if(++j == 5) {
                    progress.addAndGet(j);
                    j = 0;
                }
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
		}
        progress.addAndGet(j);
		if (list.size() > 0) {
			deleteRelationships(list, attempts - 1);
		}
	}
}
