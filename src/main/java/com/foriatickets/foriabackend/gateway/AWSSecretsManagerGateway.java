package com.foriatickets.foriabackend.gateway;

import java.util.List;
import java.util.Optional;

/**
 * AWS Secrets Manager is a key/value store to securely store secrets like API keys and passwords.
 * These methods can retrieve and cache the values.
 *
 * @author Corbin Schwalm
 */
public interface AWSSecretsManagerGateway {

    class ApiKey {
        public String key;
        public String secret;
        public List<String> scopes;
    }

    /**
     * Basic structure that provides connection information for the loaded database.
     */
    class DBInfo {
        public String username;
        public String password;
        private String engine;
        private String host;
        private String port;

        DBInfo setUsername(String username) {
            this.username = username;
            return this;
        }

        DBInfo setPassword(String password) {
            this.password = password;
            return this;
        }

        DBInfo setEngine(String engine) {
            this.engine = engine;
            return this;
        }

        DBInfo setHost(String host) {
            this.host = host;
            return this;
        }

        DBInfo setPort(String port) {
            this.port = port;
            return this;
        }

        public String getJDBCUrl(String dbName) {
            return "jdbc:" + engine + "://" + host + ":" + port + "/" + dbName + "?requireSSL=true";
        }
    }

    /**
     * Returns a list of API keys that have permission to bypass JWT authentication.
     *
     * @param keyName ID of provider using key.
     * @return Info including secret and scopes.
     */
    Optional<ApiKey> getApiKey(String keyName);

    /**
     * Returns a simple string of the stored secret.
     *
     * @param secretFriendlyName Tag
     * @return string
     */
    Optional<String> getSecretString(String secretFriendlyName);

    /**
     * Returns Db connection info as a structure.
     *
     * @param friendlyName Tag
     * @return structure
     */
    Optional<DBInfo> getDbInfo(String friendlyName);
}
