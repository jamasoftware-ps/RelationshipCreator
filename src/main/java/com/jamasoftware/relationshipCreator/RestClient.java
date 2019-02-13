package com.jamasoftware.relationshipCreator;

import org.apache.http.*;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.naming.Name;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RestClient {
    private static String token;
    private static Instant tokenAquiredAt;
    private static Long expiresInSecs;
    private static PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    private static HttpClient client;
    private static long reconnectTime = 10000;
    static {
        connectionManager.setDefaultMaxPerRoute(10);
        client = HttpClients
                .custom()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .disableConnectionState()
                .disableCookieManagement()
                .disableRedirectHandling()
                .setConnectionManager(connectionManager)
                .build();
    }

    public static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void connectionError(IOException e) {
        System.out.println("Unable to connect.  Attempting connection in 10 seconds.  Ctrl + c to end.");
        System.out.println(e.getMessage());
        e.printStackTrace();
        pause(reconnectTime);
    }

    public static void endExecutionIfUnauthorized(int httpResponseCode) {
        if(httpResponseCode == 401) {
            System.out.println("Unable to authenticate.  Check credentials and permissions and try again.");
            System.exit(1);
        }
    }

    public static Header getAuthenticationHeader(UsernamePasswordCredentials credentials, HttpRequest request) {
        try {
            return new BasicScheme().authenticate(credentials, request, null);
        } catch(AuthenticationException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public static void waitIfQuickerThanDelay(long currentTime, long markTime, int delay) {
        if (currentTime - markTime < delay) {
            pause(delay - (currentTime - markTime));
        }
    }

    public static String getEntityContentOrEmptyString(HttpEntity responseEntity) {
        try {
            return (new BufferedReader(new InputStreamReader(responseEntity.getContent()))).readLine();
        } catch(IOException | NullPointerException e) {
            return "";
        }
    }

    public static Response refineResponse(HttpResponse rawResponse) {
        return new Response(rawResponse.getStatusLine().getStatusCode(),
                getEntityContentOrEmptyString(rawResponse.getEntity()));
    }

    public static Response post(Config config, String url, String payload, UsernamePasswordCredentials credentials, int delay) {
        long markTime = System.currentTimeMillis();
        HttpPost postRequest = new HttpPost(url);
        Response response = null;
        int retries = 0;

        try {
            do {
                StringEntity body = new StringEntity(payload);
                body.setContentType("application/json");
                postRequest.setEntity(body);
                addAuthHeader(config, postRequest);
                HttpResponse rawResponse = client.execute(postRequest);
                response = refineResponse(rawResponse);
                waitIfQuickerThanDelay(System.currentTimeMillis(), markTime, delay);
            } while(response.getStatusCode() == 429 && retries++ < 10);
            endExecutionIfUnauthorized(response.getStatusCode());
            return response;

        } catch (IOException e) {
            connectionError(e);
            return post(config, url, payload, credentials, delay);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }


    public static Response delete(Config config, String url, UsernamePasswordCredentials credentials, int delay) {
        long markTime = System.currentTimeMillis();
        HttpDelete deleteRequest = new HttpDelete(url);
        Response response = null;
        int retries = 0;

        try {
            do {
                addAuthHeader(config, deleteRequest);
                HttpResponse rawResponse = client.execute(deleteRequest);
                response = refineResponse(rawResponse);
                waitIfQuickerThanDelay(System.currentTimeMillis(), markTime, delay);
            } while (response.getStatusCode() == 429 && retries++ < 10);

            endExecutionIfUnauthorized(response.getStatusCode());
            return response;

        } catch (IOException e) {
            connectionError(e);
            return delete(config, url, credentials, delay);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }

    public static Response get(Config config, String url, UsernamePasswordCredentials credentials, int delay) {
        long markTime = System.currentTimeMillis();
        HttpGet getRequest = new HttpGet(url);
        HttpEntity responseEntity = null;
        Response response = null;
        int retries = 0;

        try {
            do {
                addAuthHeader(config, getRequest);
                HttpResponse rawResponse = client.execute(getRequest);
                responseEntity = rawResponse.getEntity();
                response = refineResponse(rawResponse);
                waitIfQuickerThanDelay(System.currentTimeMillis(), markTime, delay);
            } while (response.getStatusCode() == 429 && retries++ < 10);

            endExecutionIfUnauthorized(response.getStatusCode());
            return response;

        } catch (IOException e) {
            connectionError(e);
            return get(config, url, credentials, delay);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                EntityUtils.consume(responseEntity);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        return response;
    }

    private static void addAuthHeader(Config config, HttpRequest request) throws Exception {
        //check if this is basic or oauth setup
        if(config.getCredentials() != null){
            //BASIC AUTH
            request.addHeader(getAuthenticationHeader(config.getCredentials(), request));

        }
        else{
            // OAUTH
            // Get a token
            String token = getOauthToken(config);
            // Add it to the header.
            Header oAuthHeader = new BasicHeader("Authorization", "Bearer " + token );
            request.addHeader(oAuthHeader);
        }
    }

    private static String getOauthToken(Config config) throws Exception {
        // Check if token already
        if(token != null){
            // Check if this token still has time left.
            long secondsRemaining = Duration.between(tokenAquiredAt, Instant.now()).toMillis() / 1000;
            if(secondsRemaining > 60)
                return token;
        }
        return getFreshOauthToken(config);
    }

    private static String getFreshOauthToken(Config config) throws Exception {
        String oauthServerURL = config.getBaseURL() + "oauth/token";
        HttpPost postRequest = new HttpPost(oauthServerURL);
        Response response;
        try {
            List<NameValuePair> formParams = new ArrayList<>();
            formParams.add(new BasicNameValuePair("grant_type", "client_credentials"));
            UrlEncodedFormEntity body = new UrlEncodedFormEntity(formParams);
//            body.setContentType("application/json");
            postRequest.setEntity(body);
            postRequest.addHeader(getAuthenticationHeader(new UsernamePasswordCredentials(config.getClientID(), config.getClientSecret()), postRequest));
            HttpResponse rawResponse = client.execute(postRequest);
            response = refineResponse(rawResponse);
            endExecutionIfUnauthorized(response.getStatusCode());

            JSONParser jsonparser = new JSONParser();
            JSONObject jsonResponse = (JSONObject) jsonparser.parse(response.getResponse());

            if(jsonResponse.get("access_token") != null){
                tokenAquiredAt = Instant.now();
                token = (String) jsonResponse.get("access_token");
                expiresInSecs = (Long) jsonResponse.get("expires_in");
                return token;
            }


        } catch (IOException e) {
            connectionError(e);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        throw new Exception("Failed to retrieve Oauth token");
    }
}
