package com.foriatickets.foriabackend.config;

import com.auth0.spring.security.api.JwtWebSecurityConfigurer;
import com.foriatickets.foriabackend.security.ApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.HeaderWriterFilter;

/**
 * Auth0 token validator to verify that the access tokens from the Auth0 OAuth2 authentication server are valid.
 *
 * Link: https://auth0.com/docs/quickstart/backend/java-spring-security/01-authorization
 * @author Corbin Schwalm
 */
@EnableWebSecurity
@PropertySource("classpath:auth0.properties")
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Value(value = "${auth0.apiAudience}")
    private String apiAudience;

    @Value(value = "${auth0.issuer}")
    private String issuer;

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        JwtWebSecurityConfigurer
                .forRS256(apiAudience, issuer)
                .configure(http)
                .addFilterBefore(new ApiKeyFilter(), HeaderWriterFilter.class)
                .authorizeRequests()
                    .antMatchers("/v1/user/**").hasAuthority("write:user")
                    .antMatchers(HttpMethod.POST, "/v1/register").hasAuthority("write:register")
                    .antMatchers(HttpMethod.GET, "/v1/health-check").permitAll()
                    .antMatchers( "/console/**").permitAll()
                    .anyRequest().fullyAuthenticated()
                    .and()
                .csrf()
                    .disable()
                .headers()
                    .frameOptions()
                    .disable()
                    .and()
                .httpBasic()
                    .disable()
                .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
}
