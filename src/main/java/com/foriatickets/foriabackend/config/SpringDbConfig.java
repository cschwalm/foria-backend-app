package com.foriatickets.foriabackend.config;

import com.zaxxer.hikari.HikariDataSource;
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

    @Value("${db.driver_classname}")
    private String DB_DRIVER_CLASSNAME;

    @Value("${db.jdbc}")
    private String DB_JDBC;

    @Value("${db.username}")
    private  String DB_USERNAME;

    @Value("${db.password}")
    private String DB_PASSWORD;

    @Bean(name="dataSource")
    public DataSource dataSource() {

        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName(DB_DRIVER_CLASSNAME);
        ds.setJdbcUrl(DB_JDBC);
        ds.setUsername(DB_USERNAME);
        ds.setPassword(DB_PASSWORD);
        ds.setConnectionTestQuery("SELECT 1");
        return ds;
    }
}
