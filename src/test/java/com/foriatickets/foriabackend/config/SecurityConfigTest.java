package com.foriatickets.foriabackend.config;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class SecurityConfigTest {

    private SecurityConfig securityConfig = new SecurityConfig();

    @Test
    public void configure() {
        Assert.assertNotNull(securityConfig);
    }
}