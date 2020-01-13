package com.foriatickets.foriabackend.gateway;

/**
 * Mock allows gateway interfaces to be mocked out for local development.
 *
 * @author Corbin Schwalm
 */
public interface GatewayMock extends StripeGateway, FCMGateway, AWSSimpleEmailServiceGateway, Auth0Gateway {
}
