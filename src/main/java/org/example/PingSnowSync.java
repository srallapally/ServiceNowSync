package org.example;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.auth.PingAuthenticator;
import org.example.auth.SnowAuthenticator;
import org.example.client.SnowClient;
import org.example.sync.EntitlementSyncHandler;
import org.example.sync.RoleSyncHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import static java.lang.System.exit;

public class PingSnowSync {
    private static String pingAccessToken;
    private static Properties config;
    private static SnowClient snowClient;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(PingSnowSync.class);
    private static Boolean TESTMODE = false;
    private static Boolean syncApp = false;
    private static Boolean syncRole = false;
    private static Boolean syncEntitlement = false;
    private static PingAuthenticator pingAuthenticator = null;
    private static SnowAuthenticator snowAuthenticator = null;
    private static void usage() {
        System.out.println("Usage: PingSnowSync -run -properties <full path to config.properties> -testmode true|false");
    }
    public static void main(String[] arguments) throws Exception {
        boolean hasRun = false;
        boolean hasProperties = false;
        String propertiesFileName = null;
        String testmode = "true";
        for(int i = 0; i < arguments.length; i++) {
            String arg = arguments[i].toLowerCase();
            switch(arg) {
                case "-run":
                    hasRun = true;
                    break;
                case "-properties":
                    hasProperties = true;
                    propertiesFileName = arguments[i+1];
                    break;
                case "-testmode":
                    testmode = arguments[i+1];
                    logger.debug("Using testmode '{}'", testmode);
                    if (testmode.equalsIgnoreCase("true") || testmode.equalsIgnoreCase("false")) {
                        TESTMODE = Boolean.valueOf(testmode);
                        if(TESTMODE){
                            logger.debug("Testmode enabled. Catalog operations won't be performed");
                        }
                    }
                    break;
            }
        }

        if(!hasRun || !hasProperties || propertiesFileName == null ) {
            System.err.println("Error: -run and -properties with value are mandatory");
            usage();
            exit(-1);
        }
        logger.debug("Running PingSnowSync with properties file: {}", propertiesFileName);
       if(!isBlank(propertiesFileName)) {
           config = loadConfig(new File(propertiesFileName));
           logger.debug("Checking catalog items to sync");
           String checkSync = null;
           checkSync = config.getProperty("sync.app");
           if (checkSync.equalsIgnoreCase("true") || checkSync.equalsIgnoreCase("false")) {
               syncApp = Boolean.valueOf(checkSync);
           }
           checkSync = config.getProperty("sync.entitlement");
           if (checkSync.equalsIgnoreCase("true") || checkSync.equalsIgnoreCase("false")) {
               syncEntitlement = Boolean.valueOf(checkSync);
           }
           checkSync = config.getProperty("sync.role");
           if (checkSync.equalsIgnoreCase("true") || checkSync.equalsIgnoreCase("false")) {
               syncRole = Boolean.valueOf(checkSync);
           }
           if(!syncEntitlement && !syncRole && !syncApp) {
               throw new RuntimeException("Error: At least one entity must be specified");
           }
           logger.debug("Calling Authenticate");

           pingAuthenticator= new PingAuthenticator(config);
           snowAuthenticator = new SnowAuthenticator(config);
           pingAccessToken = pingAuthenticator.authenticate();
           snowClient = snowAuthenticator.authenticate();
           if(pingAccessToken == null || snowClient == null) {
               throw new RuntimeException("PingSnowSync failed to authenticate");
           }

           logger.debug("Syncing");
           if(syncEntitlement) {
               new EntitlementSyncHandler(config,"entitlement",snowClient,pingAccessToken,TESTMODE).sync();
           }
           //if(syncApp){
           //    syncAppCatalogItems(config);
           //}
           if(syncRole){
               new RoleSyncHandler(config,"role",snowClient,pingAccessToken,TESTMODE).sync();
           }
       } else {
           usage();
       }
    }
    private static Properties loadConfig(final File configPath) throws Exception {
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            config.load(fis);
            return config;
        }
    }


    public static boolean isEmpty(final String val) {
        return val == null || (val.isEmpty());
    }
    public static boolean isBlank(final String val) {
        return val == null || isEmpty(val.trim());
    }
}