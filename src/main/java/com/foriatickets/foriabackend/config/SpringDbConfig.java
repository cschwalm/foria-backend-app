package com.foriatickets.foriabackend.config;

import com.foriatickets.foriabackend.gateway.AWSSecretsManagerGateway;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackages = "com.foriatickets.foriabackend.repositories")
@EntityScan(basePackages = "com.cryptoarbitragetraders.cryptos.entities")
@EnableTransactionManagement
public class SpringDbConfig {

    @Value("${show_sql:false}")
    private String SHOW_SQL;

    @Value("${db.jdbc:#{null}}")
    private String DB_JDBC;

    @Value("${db.username:#{null}}")
    private String DB_USERNAME;

    @Value("${db.password:#{null}}")
    private String DB_PASSWORD;

    @Value("${db.name:#{null}}")
    private String DB_NAME;

    @Value("${db.secret:#{null}}")
    private String DB_SECRET;

    @Bean(name="dataSource")
    public DataSource dataSource(@Autowired AWSSecretsManagerGateway awsSecretsManagerGateway) {

        HikariDataSource ds = new HikariDataSource();

        if (DB_SECRET != null && DB_NAME != null) {

            AWSSecretsManagerGateway.DBInfo info = awsSecretsManagerGateway.getDbInfo(DB_SECRET).orElseThrow(() -> new RuntimeException("FATAL - Failed to load database config!"));
            ds.setJdbcUrl(info.getJDBCUrl(DB_NAME));
            ds.setUsername(info.username);
            ds.setPassword(info.password);

        } else {
            ds.setJdbcUrl(DB_JDBC);
            ds.setUsername(DB_USERNAME);
            ds.setPassword(DB_PASSWORD);
        }

        ds.setConnectionTestQuery("SELECT 1");
        return ds;
    }
}
