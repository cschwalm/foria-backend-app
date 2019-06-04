package com.foriatickets.foriabackend.controllers;

import io.swagger.model.BaseApiModel;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

@SpringBootTest
public class HealthCheckApiTest {

    private HealthCheckApi healthCheckApi = new HealthCheckApi();

    @Test
    public void healthCheck() {

        ResponseEntity<BaseApiModel> actual = healthCheckApi.healthCheck();
        Assert.assertNotNull(actual);
        Assert.assertNotNull(actual.getBody());
        Assert.assertEquals("OK", actual.getBody().getMessage());
    }
}