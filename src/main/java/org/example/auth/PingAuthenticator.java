package org.example.auth;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class PingAuthenticator implements Authenticator<String> {
    private static final Logger logger = LoggerFactory.getLogger(PingAuthenticator.class);
    private final Properties config;

    public PingAuthenticator(Properties config) {
        this.config = config;
    }

    @Override
    public String authenticate() throws Exception {
        String pingAuthUrl = config.getProperty("ping.tenant.url") + "/am/oauth2/alpha/access_token";
        String fullUrl = pingAuthUrl + "?grant_type=client_credentials" +
                "&client_id=" + URLEncoder.encode(config.getProperty("ping.client.id"), StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(config.getProperty("ping.client.secret"), StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode(config.getProperty("ping.client.scope"), StandardCharsets.UTF_8);

        logger.debug("Authenticating with Ping Identity at URL: {}", pingAuthUrl);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(fullUrl);
            String response = EntityUtils.toString(client.execute(request).getEntity());

            JSONObject jsonResponse = new JSONObject(response);
            if (!jsonResponse.has("access_token")) {
                throw new RuntimeException("Failed to retrieve access token from Ping Identity.");
            }

            String accessToken = jsonResponse.getString("access_token");
            logger.debug("Successfully authenticated with Ping Identity.");
            return accessToken;
        }
    }
}
