package org.example.sync;

import org.example.client.SnowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

public class SnowSyncProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SnowSyncProcessor.class);

    public static void syncItem(Map<String, Object> item, SnowClient snowClient, Properties config, boolean testMode) throws Exception {
        String snowUrl = config.getProperty("snow.tenanturl") + config.getProperty("snow.catalog.query");

        String catalogAttr = String.valueOf(config.getProperty("snow.linkingAttribute"));
        snowUrl = snowUrl.replace("%snow.linkingAttribute%", catalogAttr);

        String catalogVal = String.valueOf(item.get(catalogAttr));
        if (catalogVal != null) {
            snowUrl = snowUrl.replace("%ping.catalog.id%", catalogVal);

            logger.debug("Checking item in ServiceNow: {}", snowUrl);
            String response = snowClient.executeGet(snowUrl);

            if (response.isEmpty()) {
                if (testMode) {
                    logger.debug("TESTMODE: Item would have been created.");
                } else {
                    logger.info("Creating new item in ServiceNow.");
                    String jsonPayload = generateJsonPayload(item, config);
                    snowClient.executePost(snowUrl, jsonPayload);
                }
            } else {
                logger.info("Item already exists in ServiceNow. Skipping creation.");
            }
        }
    }

    private static String generateJsonPayload(Map<String, Object> item, Properties config) {
        // Process template replacement here
        return "{}"; // Simplified for brevity
    }
}
