package com.jamasoftware.relationshipCreator;

import org.apache.http.auth.UsernamePasswordCredentials;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class Config {

    private UsernamePasswordCredentials credentials;
    private String clientID;
    private String clientSecret;

    private String baseURL;
    private String csvFile;
    private int columnAItemType;

    private String columnAFieldName;
    private int[] columnAProjects;
    private int columnBItemType;

    private String columnBFieldName;
    private int[] columnBProjects;
    private String delimiter;

    private int delay;
    private int retries;
    private String parseErrors = "";
    private String[] params = new String[] {
            "delimiter",
            "username",
            "password",
            "clientID",
            "clientSecret",
            "baseURL",
            "csvFile",
            "columnAItemTypeAPI-ID",
            "columnAFieldName",
            "columnAProjects",
            "columnBItemTypeAPI-ID",
            "columnBFieldName",
            "columnBProjects",
            "delay",
            "retries"
    };
    private ArrayList<String> configFile = new ArrayList<String>();
    public Config() {
        HashMap<String, String[]> params = getParams();
        if(params.get("username").length < 1 || !"".equals(params.get("username")[0])){
            System.out.println("Connecting via basic auth.");
            String username = params.get("username")[0];
            String password = params.get("password")[0];
            credentials = new UsernamePasswordCredentials(username, password);
        }
        else if(params.get("clientID") != null){
            System.out.println("Connecting via OAuth.");
            clientID = params.get("clientID")[0];
            clientSecret = params.get("clientSecret")[0];
        }


        baseURL = params.get("baseURL")[0];
        csvFile = params.get("csvFile")[0];
        try {
            columnAItemType = Integer.parseInt(params.get("columnAItemTypeAPI-ID")[0]);
        } catch (NumberFormatException e) {
            addError(params.get("columnAItemTypeAPI-ID")[0], "item type ID (a number)");
        }
        columnAFieldName = params.get("columnAFieldName")[0];
        try {
            columnBItemType = Integer.parseInt(params.get("columnBItemTypeAPI-ID")[0]);
        } catch (NumberFormatException e) {
            addError(params.get("columnBItemTypeBPI-ID")[0], "item type ID (a number)");
        }
        columnBFieldName = params.get("columnBFieldName")[0];
        delimiter = params.get("delimiter")[0];
        if(delimiter.equals("")) {
            delimiter = ",";
        }
        try {
            delay = Integer.parseInt(params.get("delay")[0]);
        } catch (NumberFormatException e) {
            addError(params.get("delay")[0], "delay (a number)");
        }
        try {
            retries = Integer.parseInt(params.get("retries")[0]);
            if(retries == 0) {
               addError("retries cannot be 0", "at least 1");
            }
        } catch (NumberFormatException e) {
            addError(params.get("retries")[0], "retries (a number)");
        }
        columnAProjects = toInts(params.get("columnAProjects"));
        columnBProjects = toInts(params.get("columnBProjects"));
        endExecutionAndReportErrors();
    }

    public UsernamePasswordCredentials getCredentials() {
        return credentials;
    }

    public String getBaseURL() {
        if(baseURL.endsWith("/")) {
            return baseURL;
        } else {
            return baseURL + "/";
        }
    }

    public String getCsvFile() {
        return csvFile;
    }

    public int getColumnAItemType() {
        return columnAItemType;
    }

    public String getColumnAFieldName() {
        return columnAFieldName;
    }

    public int[] getColumnAProjects() {
        return columnAProjects;
    }

    public int[] getColumnBProjects() {
        return columnBProjects;
    }

    public int getColumnBItemType() {
        return columnBItemType;
    }

    public String getColumnBFieldName() {
        return columnBFieldName;
    }

    public String getClientID() {
        return clientID;
    }

    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public int getDelay() {
        return delay;
    }

    public int getRetries() {
        return retries;
    }

    public int[] toInts(String[] strings) {
        int[] toReturn = new int[strings.length];
        for(int i = 0; i < strings.length; ++i) {
            try {
                toReturn[i] = Integer.parseInt(strings[i]);
            } catch (NumberFormatException e) {
                addError(strings[i], "a projectID (number)");
            }
        }
        return toReturn;
    }

    private void addError(String source, String expected) {
        parseErrors += "Error in config file: " + source + "\n\tExpected " + expected + "\n";
    }

    private void endExecutionAndReportErrors() {
        if(!parseErrors.equals("")) {
            System.out.println(parseErrors);
            System.out.println("Aborting.");
            System.out.println("Please correct config file.  If you delete it a blank one will be created.");
            System.exit(1);
        }
    }

    public void createConfigFile() throws IOException {
        BufferedWriter bw;
        FileWriter out = new FileWriter("import.cfg");
        bw = new BufferedWriter(out);
        for(String param : params) {
            bw.write(param + " = \r\n");
        }
        bw.close();
    }

    public HashMap<String, String[]> getParams() {
        try {
            getFile();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        HashMap<String, String[]> params = new HashMap<String, String[]>();

        String entireLine = configFile.get(0);
        String[] splitLine = entireLine.split("=", 2);
        if(!splitLine[0].trim().equals(this.params[0])) {
            addError("Incorrect first option", "Expected \"delimiter\" first");
            endExecutionAndReportErrors();
        }
        delimiter = splitLine[1].trim();
        if(delimiter.length() == 0) {
            addError("No delimiter specified", "a single character (likely ',' or ';')");
            endExecutionAndReportErrors();
        }
        if(delimiter.length() > 1) {
            addError("at: " + delimiter, "a single character (likely ',' or ';')");
            endExecutionAndReportErrors();
        }
        params.put(splitLine[0].trim(), new String[]{delimiter});



        for(String line : configFile) {
            String[] paramValue = line.split("=", 2);
            if(paramValue[0].trim().equals("delimiter")) {
                continue;
            }
            String[] values = paramValue[1].split(delimiter);
            for(int i = 0; i < values.length; ++i) {
                values[i] = values[i].trim();
            }
            params.put(paramValue[0].trim(), values);
        }
        if(params.size() != this.params.length) {
            addError("Incorrect number of parameters", this.params.length + ", got " + params.size());
        }
        return params;
    }

    private void getFile() throws IOException {
        FileReader in = null;
        BufferedReader br;
        try {
            in = new FileReader("import.cfg");
        } catch (FileNotFoundException e) {
            createConfigFile();
            System.out.println("New import.cfg file created.");
            System.exit(42);
        }

        br = new BufferedReader(in);
        String line = br.readLine();
        while(line != null) {
            configFile.add(line);
            line = br.readLine();
        }
    }
}
