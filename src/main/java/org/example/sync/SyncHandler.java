package org.example.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.client.SnowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
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
        logger.debug("queryTemplate: {}", queryTemplate);
        logger.debug("queryBody: {}", queryBody);
        String mappingFile = getMapping();
        logger.debug("mappingJson: {}", mappingFile);
        Properties mappingProperties = loadConfig(new File(mappingFile));

        int offset = 0;
        boolean hasMore = true;
        List<Map<String, Object>> entities = new ArrayList<>();

        while (hasMore) {
            String query = queryTemplate.replace("%offset%", String.valueOf(offset));
            logger.debug("Query for {}: {}", entityType, query);
            String pingResponse = executePingQuery(query,queryBody);
            //logger.debug("Ping response: {}", pingResponse);
            JsonNode pingItems = mapper.readTree(pingResponse).path("result");
            String totalCount = null;
            totalCount = mapper.readTree(pingResponse).get("totalCount").asText();
            logger.debug("totalCount: {}", totalCount);
            if(totalCount == null) {
                totalCount = "0";
            }
            if (pingItems.isEmpty() || offset >= Integer.parseInt(totalCount) ) {
                hasMore = false;
                continue;
            }

            for (JsonNode item : pingItems) {
                Map<String, Object> entityMap = new HashMap<>();
                for (String key : mappingProperties.stringPropertyNames()) {
                    logger.debug("{} key: {}", entityType,key);
                    String jsonPath = mappingProperties.getProperty(key);
                    logger.debug("{} jsonPath: {}",entityType, jsonPath);
                    Object value = extractValue(item, jsonPath);
                    entityMap.put(key, value);
                }
                entities.add(entityMap);
            }
            offset += Integer.parseInt(config.getProperty("ping.catalog.offset"));
        }
        logger.debug("Collected  {} {}s", entities.size(),entityType);
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

    private Object extractValue(JsonNode node, String jsonPath) {
        JsonNode current = node;
        for (String part : jsonPath.split("/")) {
            if (!part.isEmpty()) {
                current = current.path(part);
            }
        }

        if (current.isTextual()) return current.asText();
        if (current.isNumber()) return current.numberValue();
        if (current.isBoolean()) return current.asBoolean();
        if (current.isArray()) return current.toString();
        return current.toString();
    }

    protected abstract String executePingQuery(String url, String query) throws Exception;

    protected abstract  String processTemplate(Map<String, Object> entity) throws Exception;
}
