package com.foriatickets.foriabackend.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

import java.util.Collection;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private String credentials;
    private Object principal;

    ApiKeyAuthenticationToken(Object principal, String credentials, Collection<? extends GrantedAuthority> authorities) {

        super(authorities);

        if (principal == null || "".equals(principal)) {
            throw new IllegalArgumentException("principal cannot be null or empty");
        }
        Assert.notEmpty(authorities, "authorities cannot be null or empty");

        this.credentials = credentials;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
