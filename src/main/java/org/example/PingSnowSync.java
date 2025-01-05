package org.example;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

public class PingSnowSync {
    private final String pingClientId;
    private final String pingClientSecret;
    private String pingAccessToken;
    private String snowAccessToken;
    private final String pingTenantUrl;
    private final String snowUsername;
    private final String snowPassword;
    private final String snowUrl;

    private final Properties config;

    private final ObjectMapper mapper = new ObjectMapper();
    private static Logger logger = LoggerFactory.getLogger(PingSnowSync.class);

    private static final List<String> REQUIRED_CONFIG_KEYS = Arrays.asList(
            "ping.client.id",
            "ping.client.secret",
            "ping.tenant.url",
            "snow.username",
            "snow.password",
            "snow.workflowId",
            "snow.catalogId",
            "snow.app.category",
            "snow.entitlement.category",
            "snow.role.category",
            "snow.linkingAttribute",
            "snow.url",
            "ping.app.query",
            "ping.app.query.body",
            "ping.entitlement.query",
            "ping.entitlement.query.body",
            "ping.role.query",
            "ping.role.query.body",
            "ping.lastrundate",
            "ping.app.glossary.mapping",
            "ping.role.glossary.mapping",
            "ping.entitlement.glossary.mapping"
    );
    public PingSnowSync(String configPath) throws ConfigurationException {
       try {
        logger.debug("Loading configuration from {}", configPath);
        config = loadConfig(configPath);
        logger.info("Loaded configuration from {}", configPath);
        logger.debug("Loaded configuration: {}", mapper.writeValueAsString(config));
       } catch (Exception e) {
           throw new ConfigurationException("Failed to initialize PingSnowSync: " + e.getMessage(), e);
       }
    }
    private Properties loadConfig(String configPath) throws Exception {
        Properties config = new Properties();
        try {
            FileInputStream fis = new FileInputStream(configPath);
            config.load(fis);
            // Validate all required keys are present
            List<String> missingKeys = REQUIRED_CONFIG_KEYS.stream()
                    .filter(key -> !config.containsKey(key))
                    .toList();

            if (!missingKeys.isEmpty()) {
                throw new ConfigurationException("Missing required configuration keys: " + missingKeys);
            }
            return config;
        } catch (Exception e) {
                throw new ConfigurationException("Failed to load configuration: " + e.getMessage(), e);
        }
    }

    public void authenticate() throws Exception {
        // Authenticate with Ping
        String pingAuthUrl = pingTenantUrl + "/am/oauth2/alpha/access_token";
        String fullUrl = pingAuthUrl + "?grant_type=client_credentials" +
                "&client_id=" + pingClientId +
                "&client_secret=" + pingClientSecret +
                "&scope=" + URLEncoder.encode("fr:idm:* fr:iga:*", "UTF-8");
        logger.debug("Authenticating with {}", fullUrl);
        // Create HttpClient
        HttpClient httpClient = HttpClients.createDefault();
        // Create POST request
        HttpPost request = new HttpPost(fullUrl);
        // Execute request
        HttpResponse response = httpClient.execute(request);
        // Get response content
        HttpEntity entity = response.getEntity();
        String jsonResponse = EntityUtils.toString(entity);
        logger.debug("Authenticated with {}", jsonResponse);
        // Parse JSON response to get access token
        JSONObject jsonObject = new JSONObject(jsonResponse);
        pingAccessToken = jsonObject.getString("access_token");
        logger.debug("Ping access token: {}", pingAccessToken);

        // Authenticate with ServiceNow
        //String snowAuthUrl = snowUrl + "/oauth/token";
        //try (CloseableHttpClient client = HttpClients.createDefault()) {
        //    HttpPost request = new HttpPost(snowAuthUrl);
        //    request.addHeader("Content-Type", "application/x-www-form-urlencoded");

        //    StringEntity entity = new StringEntity(
        //            "grant_type=password&username=" + snowUsername +
        //                    "&password=" + snowPassword);
        //    request.setEntity(entity);

       //     String response = EntityUtils.toString(
       //             client.execute(request).getEntity());
       //     snowAccessToken = mapper.readTree(response).get("access_token").asText();
        //}
    }

    public void syncCatalogItems() throws Exception {
       // syncItems("app", queries.getProperty("app_query"),
       //         queries.getProperty("app_payload"), appTemplatePath);
       // syncItems("role", queries.getProperty("role_query"),
       //         queries.getProperty("role_payload"), roleTemplatePath);
        syncItems("entitlement",
                config.getProperty("ping.entitlement.query"),
                config.getProperty("ping.entitlement.query.body"));
    }

    private void syncItems(String type, String queryTemplate,String queryBody) throws Exception {
        int offset = 0;
        boolean hasMore = true;
        logger.debug("queryTemplate: {}", queryTemplate);
        logger.debug("queryBody: {}", queryBody);

        while (hasMore) {
            String query = String.format(queryTemplate, String.valueOf(offset));
            logger.debug("query: {}", query);

            // Get items from Ping
            String pingResponse = executePingQuery(query, queryBody);
            var pingItems = mapper.readTree(pingResponse).get("result");

            if (pingItems.isEmpty()) {
                hasMore = false;
                continue;
            }

            // Process each item
            for (var item : pingItems) {
                String catalogId = item.get("id").asText();
/*
                // Check if item exists in ServiceNow
                String snowQuery = snowUrl + "/api/now/table/sc_catalog_item?" +
                        "sysparm_query=u_iga_catalog_item_id=" + catalogId;

                String snowResponse = executeSnowQuery(snowQuery);
                var snowItems = mapper.readTree(snowResponse).get("result");

                String itemJson = processTemplate(template, item);

                if (snowItems.size() == 0) {
                    // Create new item
                    createSnowItem(itemJson);
                } else {
                    // Update existing item
                    String sysId = snowItems.get(0).get("sys_id").asText();
                    updateSnowItem(sysId, itemJson);
                }
 */
            }

            offset += 10;
        }
    }

    private String executePingQuery(String url, String payload) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            request.addHeader("Authorization", "Bearer " + pingAccessToken);
            request.addHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(payload));

            return EntityUtils.toString(client.execute(request).getEntity());
        }
    }

    private String executeSnowQuery(String url) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.addHeader("Authorization", "Bearer " + snowAccessToken);

            return EntityUtils.toString(client.execute(request).getEntity());
        }
    }

    private void createSnowItem(String itemJson) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(snowUrl + "/api/now/table/sc_catalog_item");
            request.addHeader("Authorization", "Bearer " + snowAccessToken);
            request.addHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(itemJson));

            client.execute(request);
        }
    }

    private void updateSnowItem(String sysId, String itemJson) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPut request = new HttpPut(
                    snowUrl + "/api/now/table/sc_catalog_item/" + sysId);
            request.addHeader("Authorization", "Bearer " + snowAccessToken);
            request.addHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(itemJson));

            client.execute(request);
        }
    }

    private String processTemplate(String template, String item) throws Exception {
        // Replace template variables with values from the item
        // This implementation would depend on the actual template structure
        return template.replace("${catalog_id}", "x")
                //item.get("id").asText())
                // Add other replacements based on template needs
                ;
    }
    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    public static void main(String[] args) {
        if (args.length != 10) {
            System.out.println("Required arguments: pingClientId pingClientSecret " +
                    "pingTenantUrl snowUsername snowPassword snowUrl appTemplatePath " +
                    "entitlementTemplatePath roleTemplatePath queriesPath");
            System.exit(1);
        }

        try {
            PingSnowSync sync = new PingSnowSync(
                    args[0], args[1], args[2], args[3], args[4], args[5],
                    args[6], args[7], args[8], args[9]);

            sync.authenticate();
            sync.syncCatalogItems();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}