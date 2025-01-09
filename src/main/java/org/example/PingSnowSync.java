package org.example;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PingSnowSync {
    private static String pingAccessToken;
    private static Properties config;
    private static CloseableHttpClient snowClient;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(PingSnowSync.class);
    private static Boolean TESTMODE = false;
    private static final List<String> REQUIRED_CONFIG_KEYS = Arrays.asList(
            "ping.client.id",
            "ping.client.secret",
            "ping.tenant.url",
            "ping.app.query",
            "ping.app.query.body",
            "ping.entitlement.query",
            "ping.entitlement.query.body",
            "ping.role.query",
            "ping.role.query.body",
            "ping.lastrundate",
            "ping.app.glossary.mapping",
            "ping.role.glossary.mapping",
            "ping.entitlement.glossary.mapping",
            "snow.username",
            "snow.password",
            "snow.catalogId",
            "snow.app.category",
            "snow.entitlement.category",
            "snow.role.category",
            "snow.linkingAttribute",
            "snow.tenanturl"
    );
    private static void usage() {
        System.out.println("Usage: PingSnowSync -run -properties <full path to config.properties> -testmode true|false");
    }
    public static void main(String[] arguments) throws Exception {
        if (arguments.length % 3 != 1) {
            usage();
            return;
        }
        String cmd = arguments[0];
        if (cmd.equalsIgnoreCase("-run")) {
            String propertiesFileName = getArgumentValue(arguments, "-properties");
            logger.debug("Running PingSnowSync with properties file: {}", propertiesFileName);
            if (!isBlank(propertiesFileName)) {
                config = loadConfig(new File(propertiesFileName));
                String testmode = getArgumentValue(arguments, "-testmode");
                if (testmode.equalsIgnoreCase("true") || testmode.equalsIgnoreCase("false")) {
                    TESTMODE = Boolean.valueOf(testmode);
                }
                logger.debug("Calling Authenticate");
                authenticatePing();
                authenticateSnow();
                logger.debug("Syncing");
                syncCatalogItems();

            } else {
                usage();
            }
        }

    }
    private static Properties loadConfig(final File configPath) throws Exception {
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            config.load(fis);
            return config;
        }
    }

    private static void authenticatePing() throws Exception {
        // Authenticate with Ping
        String pingAuthUrl = config.getProperty("ping.tenant.url") + "/am/oauth2/alpha/access_token";
        String fullUrl = pingAuthUrl + "?grant_type=client_credentials" +
                "&client_id=" + config.getProperty("ping.client.id") +
                "&client_secret=" + config.getProperty("ping.client.secret") +
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
    }
    private static void authenticateSnow(){
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(config.getProperty("snow.username"), config.getProperty("snow.password"))
        );

        snowClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();
        logger.debug("Authenticating with Service Now");
    }
    public static void syncCatalogItems() throws Exception {
       // syncItems("app", queries.getProperty("app_query"),
       //         queries.getProperty("app_payload"), appTemplatePath);
       // syncItems("role", queries.getProperty("role_query"),
       //         queries.getProperty("role_payload"), roleTemplatePath);
        syncItems("entitlement",
                config.getProperty("ping.tenant.url")+ config.getProperty("ping.entitlement.query"),
                config.getProperty("ping.entitlement.query.body"));
    }

    private static void syncItems(String type, String queryTemplate, String queryBody) throws Exception {
        int offset = 0;
        boolean hasMore = true;
        ArrayList<Map<String, Object>> entitlements = new ArrayList<>();
        logger.debug("queryTemplate: {}", queryTemplate);
        logger.debug("queryBody: {}", queryBody);
        Properties mappingProperties = new Properties();
        String mappingJson = config.getProperty("snow.catalog.mapping");
        logger.debug("mappingJson: {}", mappingJson);
        mappingProperties = loadConfig(new File(mappingJson));
        while (hasMore) {
            String query = queryTemplate.replace("%offset%", String.valueOf(offset));
            logger.debug("query: {}", query);
            // Get items from Ping
            String pingResponse = executePingQuery(query, queryBody);
            //System.out.println("PingResponse: " + pingResponse);
            JsonNode pingItems = mapper.readTree(pingResponse);
            JsonNode results = pingItems.path("result");
            String totalCount = null;
            totalCount = pingItems.path("totalCount").asText();
            logger.debug("totalCount: {}", totalCount);
            if(totalCount == null) {
                totalCount = "0";
            }
            if (pingItems.isEmpty() || offset >= Integer.parseInt(totalCount) ) {
                hasMore = false;
                continue;
            }
            // Process each item
            for (JsonNode item : results) {
                Map<String, Object> entitlementMap = new HashMap<>();
                // Extract values based on mappings
                for (String key : mappingProperties.stringPropertyNames()) {
                    String jsonPath = mappingProperties.getProperty(key);
                    Object value = extractValue(item, jsonPath);
                    entitlementMap.put(key, value);
                }
                entitlements.add(entitlementMap);
            }
            offset += Integer.parseInt(config.getProperty("ping.catalog.offset"));
        }

        for (Map<String, Object> entitlement : entitlements) {
            String snowUrl = config.getProperty("snow.tenanturl")+config.getProperty("snow.catalog.query");
            String catalogAttr = String.valueOf(config.getProperty("snow.linkingAttribute"));
            snowUrl = snowUrl.replace("%snow.linkingAttribute%",catalogAttr);
            String catalogVal = (String) entitlement.get(catalogAttr);
            System.out.println(catalogAttr+": "+catalogVal);
            if(catalogVal != null)
                snowUrl = snowUrl.replace("%ping.catalog.id%",catalogVal);
                logger.debug("Snow URL: {}", snowUrl);
            //System.out.println("Url:  " + snowUrl);
            String snowResponse = executeSnowQuery(snowUrl);
            logger.debug("Snow Response: {}", snowResponse);
            //System.out.println(snowResponse);
            var snowItems = mapper.readTree(snowResponse).get("result");

            if (snowItems.isEmpty()) {
                logger.debug("No snow items found");
                entitlement.put("snow.tenanturl",String.valueOf(config.getProperty("snow.tenanturl")));
                entitlement.put("snow.entitlement.category",String.valueOf(config.getProperty("snow.entitlement.category")));
                entitlement.put("snow.catalogId",String.valueOf(config.getProperty("snow.catalogId")));
                String itemJson = processTemplate(entitlement);
                logger.debug("ItemJson: {}", itemJson);
                //System.out.println(itemJson);
                // Create new item
                String postResponse = createSnowItem(itemJson);
                if(TESTMODE){
                    logger.debug("TESTMODE skipped creation");
                } else {
                    logger.debug("Item postResponse: {}", postResponse);
                }
            } else {
                System.out.println("Found " + snowItems.size() + " snow items");
                // Update existing item
                //String sysId = snowItems.get(0).get("sys_id").asText();
                //updateSnowItem(sysId, itemJson);
            }
            System.out.println("Sleeping");
            TimeUnit.SECONDS.toMillis(2);
        }
    }
    private static Object extractValue(JsonNode node, String jsonPath) {
        String[] pathParts = jsonPath.split("/");
        System.out.println(pathParts[0]);
        JsonNode current = node;
        for (String part : pathParts) {
            //System.out.println("Checking for "+part);
            if (part.isEmpty()) continue;

            current = current.path(part);

            if (current.isMissingNode()) {
                //System.out.println(part + " is empty");
                return null; // Path does not exist
            }
        }

        if (current.isTextual()) {
            return current.asText();
        } else if (current.isNumber()) {
            return current.numberValue();
        } else if (current.isBoolean()) {
            return current.asBoolean();
        } else if (current.isArray()) {
            return current.toString(); // Convert arrays to string for simplicity
        }
        return current.toString();
    }
    private static String executePingQuery(String url, String payload) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            request.addHeader("Authorization", "Bearer " + pingAccessToken);
            request.addHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(payload));

            return EntityUtils.toString(client.execute(request).getEntity());
        }
    }
    private static String executeSnowQuery(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.setHeader(HttpHeaders.ACCEPT, "application/json");

        try (CloseableHttpResponse response = snowClient.execute(request)) {
            return EntityUtils.toString(snowClient.execute(request).getEntity());
        }
    }

    private static String createSnowItem(String jsonBody) throws Exception {
        String snowUrl = config.getProperty("snow.tenanturl")+config.getProperty("snow.catalog.create");
        logger.debug("Snow URL: {}", snowUrl);
        if(!TESTMODE) {
            //System.out.println(snowUrl);
            HttpPost post = new HttpPost(snowUrl);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Accept", "application/json");
            // Set the JSON body
            StringEntity entity = new StringEntity(jsonBody);
            logger.debug("body:\n" + EntityUtils.toString(entity));
            post.setEntity(entity);
            HttpResponse response = snowClient.execute(post);
            return EntityUtils.toString(response.getEntity());
        } else {
            logger.debug("Skipping snow item creation due to test mode");
        }
        return null;
    }
    private static String processTemplate(Map<String, Object> entitlement) throws Exception {
        String templName = config.getProperty("snow.entitlement.create.template");
        String templ = null;
        // Read the file from resources folder
        try (InputStream inputStream = new FileInputStream(templName)) {
            // Read the content using UTF-8 encoding
            templ = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (Map.Entry<String, Object> entry : entitlement.entrySet()) {
            templ = templ.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return templ;
    }
    private static String getArgumentValue(String[] arguments, String keyName) {
        if (arguments.length >= 2) {
            for (int i = 0; i < arguments.length - 1; i++) {
                String name = arguments[i];
                String value = arguments[i + 1];
                if (name.equalsIgnoreCase(keyName)) {
                    return value;
                }
            }
        }
        return null;
    }
    public static boolean isEmpty(final String val) {
        return val == null || (val.isEmpty());
    }
    public static boolean isBlank(final String val) {
        return val == null || isEmpty(val.trim());
    }
    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}