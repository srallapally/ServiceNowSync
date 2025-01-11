#!/bin/bash

# Check Java availability
if type -p 'java' >/dev/null; then
    JAVA=java
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ];  then
    JAVA="$JAVA_HOME/bin/java"
else
    echo JAVA_HOME not available, Java is needed to run the Connector Server
    echo Please install Java and set the JAVA_HOME accordingly
    exit 1
fi
# Get the third argument for properties file
PROPERTIES_FILE="${3:-./config/config.properties}"  # Use default if not provided
TESTMODE="${5:-false}"  # Use default if not provided

MAIN_CLASS=org.example.PingSnowSync
CLASSPATH="./lib/*"
exec $JAVA -classpath "$CLASSPATH" \
-Dlogback.configurationFile=./config/logback.xml \
$MAIN_CLASS -run \
-properties "$PROPERTIES_FILE" \
-testmode "$TESTMODE"