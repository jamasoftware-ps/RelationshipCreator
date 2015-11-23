package com.jamasoftware.relationshipCreator;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class RestClient {
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

    public static void connectionError() {
        System.out.println("Unable to connect.  Attempting connection in 10 seconds.  Ctrl + c to end.");
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

    public static Response post(String url, String payload, UsernamePasswordCredentials credentials, int delay) {
        long markTime = System.currentTimeMillis();
        HttpPost postRequest = new HttpPost(url);
        Response response;
        int retries = 0;

        try {
            do {
                StringEntity body = new StringEntity(payload);
                body.setContentType("application/json");
                postRequest.setEntity(body);
                postRequest.addHeader(getAuthenticationHeader(credentials, postRequest));
                HttpResponse rawResponse = client.execute(postRequest);
                response = refineResponse(rawResponse);
                waitIfQuickerThanDelay(System.currentTimeMillis(), markTime, delay);
            } while(response.getStatusCode() == 429 && retries++ < 10);
            endExecutionIfUnauthorized(response.getStatusCode());
            return response;

        } catch (IOException e) {
            connectionError();
            return post(url, payload, credentials, delay);
        }
    }

    public static Response delete(String url, UsernamePasswordCredentials credentials, int delay) {
        long markTime = System.currentTimeMillis();
        HttpDelete deleteRequest = new HttpDelete(url);
        Response response;
        int retries = 0;

        try {
            do {
                deleteRequest.addHeader(getAuthenticationHeader(credentials, deleteRequest));
                HttpResponse rawResponse = client.execute(deleteRequest);
                response = refineResponse(rawResponse);
                waitIfQuickerThanDelay(System.currentTimeMillis(), markTime, delay);
            } while (response.getStatusCode() == 429 && retries++ < 10);

            endExecutionIfUnauthorized(response.getStatusCode());
            return response;

        } catch (IOException e) {
            connectionError();
            return delete(url, credentials, delay);
        }
    }

    public static Response get(String url, UsernamePasswordCredentials credentials, int delay) {
        long markTime = System.currentTimeMillis();
        HttpGet getRequest = new HttpGet(url);
        HttpEntity responseEntity = null;
        Response response;
        int retries = 0;

        try {
            do {
                getRequest.addHeader(getAuthenticationHeader(credentials, getRequest));
                HttpResponse rawResponse = client.execute(getRequest);
                responseEntity = rawResponse.getEntity();
                response = refineResponse(rawResponse);
                waitIfQuickerThanDelay(System.currentTimeMillis(), markTime, delay);
            } while (response.getStatusCode() == 429 && retries++ < 10);

            endExecutionIfUnauthorized(response.getStatusCode());
            return response;

        } catch (IOException e) {
            connectionError();
            return get(url, credentials, delay);

        } finally {
            try {
                EntityUtils.consume(responseEntity);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
