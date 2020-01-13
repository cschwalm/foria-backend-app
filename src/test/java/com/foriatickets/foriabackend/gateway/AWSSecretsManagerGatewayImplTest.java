package com.foriatickets.foriabackend.gateway;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class AWSSecretsManagerGatewayImplTest {

    @Mock
    private SecretsManagerClient secretsManagerClient;

    private AWSSecretsManagerGateway awsSecretsManagerGateway = new AWSSecretsManagerGatewayImpl();

    private String testName = "testKey";

    @Before
    public void setUp() {

        String testJson = "{\n" +
                "  \"username\":\"test\",\n" +
                "  \"password\":\"pass\",\n" +
                "  \"host\":\"host\",\n" +
                "  \"engine\":\"mysql\",\n" +
                "  \"port\":\"3306\"\n" +
                "}";
        GetSecretValueResponse getSecretValueResponseMock = GetSecretValueResponse.builder().secretString(testJson).build();

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponseMock);
        awsSecretsManagerGateway = new AWSSecretsManagerGatewayImpl();
        ReflectionTestUtils.setField(awsSecretsManagerGateway, "secretsManagerClient", secretsManagerClient);
    }

    @Test
    public void getSecretString() {

        Assert.assertTrue(awsSecretsManagerGateway.getDbInfo(testName).isPresent());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void getDbInfo() {

        Optional<AWSSecretsManagerGateway.DBInfo> info = awsSecretsManagerGateway.getDbInfo(testName);
        Assert.assertNotNull(info.get());
    }

    @Test
    public void getAllSecrets() {

        Optional<Map<String, String>> info = awsSecretsManagerGateway.getAllSecrets(testName);
        if (!info.isPresent()) {
            fail();
        }

        Map<String, String> secrets = info.get();
        Assert.assertNotNull(secrets);
    }
}