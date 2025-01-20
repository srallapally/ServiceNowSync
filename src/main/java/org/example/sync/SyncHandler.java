package org.example.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.example.client.SnowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public abstract class SyncHandler {
    protected Properties config;
    protected String entityType;
    protected ObjectMapper mapper = new ObjectMapper();
    protected String pingAccessToken;
    protected SnowClient snowClient;
    protected Boolean testmode;
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    public SyncHandler(Properties config, String entityType, SnowClient snowClient, String pingAccessToken, Boolean testmode) {
        this.config = config;
        this.entityType = entityType;
        this.snowClient = snowClient;
        this.pingAccessToken = pingAccessToken;
        this.testmode = testmode;
    }

    public abstract void sync() throws Exception;

    protected abstract String getQueryTemplate();
    protected abstract String getQueryBody();
    protected abstract String getMapping();

    protected abstract String getCreateTemplate();
    protected abstract String getUpdateTemplate();

    protected void executeSync() throws Exception {
        logger.debug("Starting sync for {}", entityType);
        String queryTemplate = getQueryTemplate();
        String queryBody = getQueryBody();
        //logger.debug("queryTemplate: {}", queryTemplate);
        //logger.debug("queryBody: {}", queryBody);
        String mappingFile = getMapping();
        //logger.debug("mappingJson: {}", mappingFile);
        Properties mappingProperties = loadConfig(new File(mappingFile));

        int offset = 0;
        boolean hasMore = true;
        List<Map<String, Object>> entities = new ArrayList<>();

        while (hasMore) {
            String query = queryTemplate.replace("%offset%", String.valueOf(offset));
            logger.info("Query for {}: {}", entityType, query);
            String pingResponse = executePingQuery(query,queryBody);
           // logger.debug("Ping response: {}", pingResponse);
            JsonNode pingItems = mapper.readTree(pingResponse).path("result");
           int totalCount = 0;
           try {
               totalCount = Integer.parseInt(mapper.readTree(pingResponse).get("totalCount").asText());
               logger.info("totalCount: {}", totalCount);
           } catch (Exception e){
               totalCount = 0;
           }
            if (pingItems.isEmpty() || offset >= totalCount ) {
                hasMore = false;
                continue;
            }

            for (JsonNode item : pingItems) {
                Map<String, Object> entityMap = new HashMap<>();
                for (String key : mappingProperties.stringPropertyNames()) {
                    //logger.debug("{} key: {}", entityType,key);
                    String jsonPath = mappingProperties.getProperty(key);
                    logger.debug("{} jsonPath: {}",entityType, jsonPath);
                    Object value = extractValue(item, jsonPath);
                    logger.debug("Key {} value: {}", key, value.toString());
                    entityMap.put(key, value);
                }
                entities.add(entityMap);
            }
            offset += Integer.parseInt(config.getProperty("ping.catalog.offset"));
        }
        logger.info("Collected  {} {}s", entities.size(),entityType);
        for (Map<String, Object> entity : entities) {
            processEntity(entity);
        }
    }

    protected abstract void processEntity(Map<String, Object> entity) throws Exception;

    private Properties loadConfig(File configPath) throws Exception {
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            config.load(fis);
        }
        return config;
    }
    protected Object extractValue(JsonNode node, String jsonPath) {
        JsonNode current = node;
        logger.debug("Current : {}", node.toPrettyString());

        if (jsonPath.startsWith("/")) {
         jsonPath = jsonPath.substring(1);
         }
        String[] tokens = jsonPath.split("/");

        logger.debug("Tokens:{}",Arrays.toString(tokens));

        // TESTING FIX Normalize the path first
        //String normalizedPath = jsonPath.replace("//", "/");
        // Split while preserving empty tokens
        //String[] tokens = normalizedPath.split("/", -1);
        // END TESTING FIX
        for (int i = 0; i < tokens.length; i++) {
            String part = tokens[i];
            logger.debug("Part:{}",part);
            // If the current token is empty, combine it with the next token
            // so that a path like "//entitlement" becomes "/entitlement".
            if (part.isEmpty()) {
                if (i + 1 < tokens.length) {
                    part = "/" + tokens[++i];
                } else {
                    // No next token to combine; break or return null as needed
                    break;
                }
            }
            current = current.path(part);
        }
        logger.debug("Current : {}", current.toPrettyString());
        // Return primitive or textual values properly
        if (current.isTextual())  return current.asText();
        if (current.isNumber())   return current.numberValue();
        if (current.isBoolean())  return current.asBoolean();
        if (current.isArray())    return current.toString();
        return current.toString();
    }

    protected abstract String executePingQuery(String url, String query) throws Exception;

    protected abstract  String processTemplate(Map<String, Object> entity) throws Exception;
}
