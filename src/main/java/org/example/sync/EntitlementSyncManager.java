package org.example.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.client.PingGovernanceClient;
import org.example.client.SnowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class EntitlementSyncManager implements EntitySyncManager {
    private static final Logger logger = LoggerFactory.getLogger(EntitlementSyncManager.class);
    private final Properties config;
    private final String pingToken;
    private final SnowClient snowClient;
    private final boolean testMode;
    private final ObjectMapper mapper = new ObjectMapper();

    public EntitlementSyncManager(Properties config, String pingToken, SnowClient snowClient, boolean testMode) {
        this.config = config;
        this.pingToken = pingToken;
        this.snowClient = snowClient;
        this.testMode = testMode;
    }

    @Override
    public void sync() throws Exception {
        String queryTemplate = config.getProperty("ping.entitlement.query");
        String queryBody = config.getProperty("ping.entitlement.query.body");

        logger.debug("Fetching entitlements using query template: {}", queryTemplate);

        List<Map<String, Object>> entitlements = PingGovernanceClient.fetchItems(pingToken, queryTemplate, queryBody, mapper);
        logger.info("Fetched {} entitlements from Ping.", entitlements.size());

        for (Map<String, Object> entitlement : entitlements) {
            SnowSyncProcessor.syncItem(entitlement, snowClient, config, testMode);
        }
    }
}
