package org.example.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PingGovernanceClient {
    private static final Logger logger = LoggerFactory.getLogger(PingGovernanceClient.class);

    public static List<Map<String, Object>> fetchItems(String token, String queryTemplate, String queryBody, ObjectMapper mapper) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        int offset = 0;
        boolean hasMore = true;

        while (hasMore) {
            String query = queryTemplate.replace("%offset%", String.valueOf(offset));
            String response = executePingQuery(token, query, queryBody);

            JsonNode rootNode = mapper.readTree(response);
            JsonNode items = rootNode.path("result");

            if (items.isEmpty()) {
                hasMore = false;
                break;
            }

            items.forEach(item -> results.add(mapper.convertValue(item, Map.class)));
            offset += Integer.parseInt(rootNode.path("totalCount").asText("0"));
        }

        return results;
    }

    private static String executePingQuery(String token, String url, String payload) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            request.addHeader("Authorization", "Bearer " + token);
            request.addHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(payload));

            return EntityUtils.toString(client.execute(request).getEntity());
        }
    }
}
