package com.foriatickets.foriabackend.config;

import com.auth0.spring.security.api.JwtWebSecurityConfigurer;
import com.foriatickets.foriabackend.security.ApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Auth0 token validator to verify that the access tokens from the Auth0 OAuth2 authentication server are valid.
 *
 * Link: https://auth0.com/docs/quickstart/backend/java-spring-security/01-authorization
 * @author Corbin Schwalm
 */
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Value(value = "${auth0.apiAudience}")
    private String apiAudience;

    @Value(value = "${auth0.issuer}")
    private String issuer;

    private static final String[] ALLOWED_CORS_ORIGINS = { "http://localhost:3000", "https://events.foriatickets.com", "https://foria.ngrok.io", "https://events-test.foriatickets.com" };

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration().applyPermitDefaultValues();
        config.setAllowedOrigins(Arrays.asList(ALLOWED_CORS_ORIGINS));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        JwtWebSecurityConfigurer
                .forRS256(apiAudience, issuer)
                .configure(http)
                .addFilterBefore(new ApiKeyFilter(), HeaderWriterFilter.class)
                .authorizeRequests()
                    .antMatchers(HttpMethod.POST, "/v1/register").hasAuthority("write:register")
                    .antMatchers(HttpMethod.GET, "/v1/health-check").permitAll()
                    .antMatchers(HttpMethod.GET, "/v1/event/**").permitAll()
                    .antMatchers(HttpMethod.OPTIONS, "/v1/event/**").permitAll()
                    .antMatchers(HttpMethod.POST, "/v1/event/*/ticketTypeConfig/promo").permitAll()
                    .antMatchers(HttpMethod.POST, "/v1/event").hasAuthority("write:event")
                    .antMatchers(HttpMethod.PUT, "/v1/event/*").hasAuthority("write:event")
                    .antMatchers(HttpMethod.PUT, "/v1/event/*/cancel").hasAuthority("write:event_cancel")
                    .antMatchers(HttpMethod.GET, "/v1/event/*/attendees").hasAuthority("read:venue")
                    .antMatchers(HttpMethod.POST, "/v1/event/*/ticketFeeConfig").hasAuthority("write:event")
                    .antMatchers(HttpMethod.POST, "/v1/event/*/ticketTypeConfig").hasAuthority("write:event")
                    .antMatchers(HttpMethod.PUT, "/v1/event/*/ticketTypeConfig/promo").hasAuthority("write:event")
                    .antMatchers(HttpMethod.DELETE, "/v1/event/*/ticketFeeConfig/*").hasAuthority("write:event")
                    .antMatchers(HttpMethod.DELETE, "/v1/event/*/ticketTypeConfig/*").hasAuthority("write:event")
                    .antMatchers(HttpMethod.GET, "/v1/venue").hasAuthority("read:venue")
                    .antMatchers(HttpMethod.POST, "/v1/venue").hasAuthority("write:venue")
                    .antMatchers(HttpMethod.POST, "/v1/venue/*/authorize/**").hasAuthority("write:venue")
                    .antMatchers(HttpMethod.POST, "/v1/venue/*/deauthorize/**").hasAuthority("write:venue")
                    .antMatchers(HttpMethod.POST, "/v1/ticket/*/manualRedeem").hasAuthority("write:venue_redeem")
                    .antMatchers(HttpMethod.POST, "/v1/ticket/redeem").hasAuthority("write:venue_redeem")
                    .antMatchers(HttpMethod.DELETE, "/v1/ticket/*/refund").hasAuthority("write:venue")
                    .antMatchers(HttpMethod.GET, "/v1/user/music/topArtists/*").permitAll()
                    .antMatchers(HttpMethod.OPTIONS, "/v1/user/music/topArtists/*/").permitAll()
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
                .cors()
                    .and()
                .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
}
