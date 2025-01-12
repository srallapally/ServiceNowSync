package org.example.sync;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.example.client.SnowClient;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class EntitlementSyncHandler extends SyncHandler {

    public EntitlementSyncHandler(Properties config, String entityType,SnowClient snowClient, String pingAccessToken,Boolean testmode) {
        super(config, "entitlement",snowClient,pingAccessToken,testmode);
    }

    @Override
    public void sync() throws Exception {
        logger.debug("Syncing entitlements...");
        executeSync();
    }

    @Override
    protected String getQueryTemplate() {
        return config.getProperty("ping.tenant.url")+ config.getProperty("ping.entitlement.query");
    }

    @Override
    protected String getQueryBody() {
        return config.getProperty("ping.entitlement.query.body");
    }

    @Override
    protected String getMapping() {
        return config.getProperty("snow.entitlement.catalog.mapping");
    }

    @Override
    protected String getCreateTemplate() {
        return config.getProperty("snow.entitlement.create.template");
    }

    @Override
    protected String getUpdateTemplate() {
        return "";
    }

    @Override
    protected void processEntity(Map<String, Object> entity) throws Exception {
        String snowUrl = config.getProperty("snow.tenanturl")+config.getProperty("snow.entitlement.catalog.query");
        logger.debug("snowUrl: {}", snowUrl);
        String catalogAttr = String.valueOf(config.getProperty("snow.entitlement.linkingAttribute"));
        logger.debug("catalogAttr: {}", catalogAttr);
        snowUrl = snowUrl.replace("%snow.entitlement.linkingAttribute%",catalogAttr);
        logger.debug("snowUrl: {}", snowUrl);
        String catalogVal = (String) entity.get(catalogAttr);
        logger.debug("catalogVal: {}", catalogVal);
        if(catalogVal != null)
            snowUrl = snowUrl.replace("%ping.entitlement.catalog_id%",catalogVal);
        logger.debug("After replacing Snow URL: {}", snowUrl);
        String snowResponse = snowClient.executeGet(snowUrl);
        logger.debug("Snow Response: {}", snowResponse);
        //System.out.println(snowResponse);
        var snowItems = mapper.readTree(snowResponse).get("result");

        if (snowItems.isEmpty()) {
            logger.debug("No snow items found");
            entity.put("snow.tenanturl",String.valueOf(config.getProperty("snow.tenanturl")));
            entity.put("snow.entitlement.category",String.valueOf(config.getProperty("snow.entitlement.category")));
            entity.put("snow.catalogId",String.valueOf(config.getProperty("snow.catalogId")));
            String itemJson = processTemplate(entity);
            logger.debug("ItemJson: {}", itemJson);
            String snowCreateUrl = config.getProperty("snow.tenanturl")+config.getProperty("snow.catalog.create");
            String postResponse = snowClient.createSnowItem(snowCreateUrl,itemJson,testmode);
            if(testmode){
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

    @Override
    protected String executePingQuery(String url, String query) throws Exception {
        logger.debug("Executing Ping Query");
        logger.debug("url: {}", url);
        logger.debug("query: {}", query);
        logger.debug("Access Token:{}",pingAccessToken);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            request.addHeader("Authorization", "Bearer " + pingAccessToken);
            request.addHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(query));
            return EntityUtils.toString(client.execute(request).getEntity());
        }
    }

    @Override
    protected String processTemplate(Map<String, Object> entity) throws Exception {
        String templName = getCreateTemplate();
        String templ = null;
        // Read the file from resources folder
        try (InputStream inputStream = new FileInputStream(templName)) {
            // Read the content using UTF-8 encoding
            templ = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (Map.Entry<String, Object> entry : entity.entrySet()) {
            templ = templ.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        // workflow
        templ = templ.replace("%snow.entitlement.workflow_id%", config.getProperty("snow.entitlement.workflow_id"));
        //icon and picture
        templ = templ.replace("%snow.entitlement_icon_id%", config.getProperty("snow.entitlement_icon_id"));
        return templ;
    }
}
