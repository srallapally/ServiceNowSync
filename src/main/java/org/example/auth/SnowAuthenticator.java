package org.example.auth;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.example.client.SnowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class SnowAuthenticator implements Authenticator<SnowClient> {
    private static final Logger logger = LoggerFactory.getLogger(SnowAuthenticator.class);
    private final Properties config;

    public SnowAuthenticator(Properties config) {
        this.config = config;
    }

    @Override
    public SnowClient authenticate() {
        String username = config.getProperty("snow.username");
        String password = config.getProperty("snow.password");

        if (username == null || password == null) {
            throw new IllegalArgumentException("ServiceNow username or password is not configured.");
        }

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password)
        );

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();

        logger.debug("Successfully authenticated with ServiceNow.");
        return new SnowClient(httpClient);
    }
}
