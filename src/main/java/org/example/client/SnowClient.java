package org.example.client;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnowClient {
    private static final Logger logger = LoggerFactory.getLogger(SnowClient.class);
    private final CloseableHttpClient httpClient;

    public SnowClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String executeGet(String url) throws Exception {
        logger.debug("Executing GET request to URL: {}", url);

        HttpGet request = new HttpGet(url);
        request.addHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("ServiceNow GET request failed with status code: " + statusCode);
            }
            return EntityUtils.toString(response.getEntity());
        }
    }

    public String executePost(String url, String jsonBody) throws Exception {
        logger.debug("Executing POST request to URL: {}", url);

        HttpPost request = new HttpPost(url);
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Accept", "application/json");
        request.setEntity(new StringEntity(jsonBody));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 201) { // Assuming 201 Created for POST
                throw new RuntimeException("ServiceNow POST request failed with status code: " + statusCode);
            }
            return EntityUtils.toString(response.getEntity());
        }
    }

    public String createSnowItem(String url, String jsonBody, Boolean testmode) throws Exception {
        logger.debug("Snow URL: {}", url);
        logger.debug("Creating Snow Item");
        logger.debug("jsonBody: {}", jsonBody);
        if(!testmode) {
            return executePost(url, jsonBody);
        } else {
            logger.debug("Skipping snow item creation due to test mode");
        }
        return null;
    }
}
