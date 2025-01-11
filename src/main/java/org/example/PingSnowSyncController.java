package org.example;
import org.example.auth.Authenticator;
import org.example.auth.PingAuthenticator;
import org.example.auth.SnowAuthenticator;
import org.example.client.SnowClient;

import java.util.Properties;

public class PingSnowSyncController {
    private final Properties config;
    private final Authenticator<String> pingAuthenticator;
    private final Authenticator<SnowClient> snowAuthenticator;

    public PingSnowSyncController(Properties config) {
        this.config = config;
        this.pingAuthenticator = new PingAuthenticator(config);
        this.snowAuthenticator = new SnowAuthenticator(config);
    }

    public void runSync(boolean syncApp, boolean syncRole, boolean syncEntitlement, boolean testMode) throws Exception {
        System.out.println("Starting synchronization process...");

        String pingToken = pingAuthenticator.authenticate();
        SnowClient snowClient = snowAuthenticator.authenticate();

        System.out.println("Synchronization process completed.");
    }
}
