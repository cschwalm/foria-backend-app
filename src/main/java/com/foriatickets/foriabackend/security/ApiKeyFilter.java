package com.foriatickets.foriabackend.security;

import com.foriatickets.foriabackend.gateway.AWSSecretsManagerGateway;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ApiKeyFilter extends OncePerRequestFilter {

    private Environment environment;

    private AWSSecretsManagerGateway awsSecretsManagerGateway;

    private static final Logger LOG = LogManager.getLogger();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if(awsSecretsManagerGateway == null) {
            ServletContext servletContext = request.getServletContext();
            WebApplicationContext webApplicationContext = WebApplicationContextUtils.getWebApplicationContext(servletContext);
            assert(webApplicationContext != null);
            environment = webApplicationContext.getBean(Environment.class);
            awsSecretsManagerGateway = webApplicationContext.getBean(AWSSecretsManagerGateway.class);
        }

        String apiKeyListString = environment.getProperty("apiKeyList");
        Map<String, AWSSecretsManagerGateway.ApiKey> keyMap = new HashMap<>();
        if (apiKeyListString == null) {

            List<String> scopes = new ArrayList<>();
            scopes.add("write:user_create");
            scopes.add("write:venue");
            scopes.add("write:event");

            LOG.debug("API key list not provided. Using test:test for API key.");
            AWSSecretsManagerGateway.ApiKey localKey = new AWSSecretsManagerGateway.ApiKey();
            localKey.key = localKey.secret = "test";
            localKey.scopes = scopes;
            keyMap.put(localKey.key, localKey);

        } else {

            //Load acceptable API keys from secrets manager
            for (String apiKey : apiKeyListString.split(";")) {
                Optional<AWSSecretsManagerGateway.ApiKey> apiKeyInfo = awsSecretsManagerGateway.getApiKey(apiKey);
                if (apiKeyInfo.isPresent()) {
                    AWSSecretsManagerGateway.ApiKey apiKeyObj = apiKeyInfo.get();
                    if (apiKeyObj.key == null || apiKeyObj.secret == null || apiKeyObj.scopes == null) {
                        LOG.error("Failed to parse API key object. Bad data in secrets manager!");
                        filterChain.doFilter(request, response);
                        return;
                    }

                    keyMap.put(apiKeyObj.key, apiKeyObj);
                }
            }
        }

        final String authorization = request.getHeader("Authorization");
        final String prefix = "ApiKey ";

        //Checks for standard HTTP authentication header with our custom prefix.
        if (StringUtils.hasText(authorization) && authorization.startsWith(prefix)) {

            String[] value = authorization.substring(prefix.length()).split(":");
            if (value.length != 2) {
                LOG.warn("Attempted to check authorization header in invalid format.");
                filterChain.doFilter(request, response);
                return;
            }
            String key = value[0];
            String secret = value[1];

            if (keyMap.containsKey(key) && keyMap.get(key).secret.equals(secret)) {

                List<SimpleGrantedAuthority> authorityList;
                List<String> scopes = keyMap.get(key).scopes;
                authorityList = scopes.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());

                ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(key, secret, authorityList);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}