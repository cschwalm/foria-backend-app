package com.foriatickets.foriabackend.gateway;

import com.google.firebase.messaging.Notification;
import com.stripe.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openapitools.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Profile("mock")
@Service
public class GatewayMockImpl implements GatewayMock {

    private static final Logger LOG = LogManager.getLogger();

    @Override
    public Charge chargeCustomer(String stripeCustomerId, String stripeToken, UUID orderId, BigDecimal amount, String currencyCode) {
        Charge charge = new Charge();
        charge.setAmount(amount.toBigInteger().longValueExact());
        charge.setCurrency(currencyCode);
        charge.setCustomer(stripeCustomerId);
        charge.setId("charge_mock_id_" + UUID.randomUUID());
        LOG.info("Stripe mock in use. Customer not charged.");
        return charge;
    }

    @Override
    public Customer createStripeCustomer(User user, String paymentToken) {

        Customer customer = new Customer();
        customer.setDefaultSource(paymentToken);
        customer.setId("customer_mock_id_" + UUID.randomUUID());
        LOG.info("Stripe mock in use. Customer not created.");
        return customer;
    }

    @Override
    public Refund refundStripeCharge(String chargeRefId, BigDecimal amountToRefund) {
        Refund refund = new Refund();
        refund.setAmount(123L);
        refund.setCharge(chargeRefId);
        refund.setAmount(amountToRefund.longValue());

        return refund;
    }

    @Override
    public StripeGatewayImpl.SettlementInfo getSettlementInfo() {

        Payout payout = new Payout();
        payout.setAmount(1234L);
        payout.setAutomatic(true);
        payout.setCurrency("USD");
        payout.setId("TEST");
        payout.setStatus("completed");

        List<BalanceTransaction> balanceTransactionList = new ArrayList<>();
        BalanceTransaction balanceTransaction = new BalanceTransaction();
        balanceTransaction.setAmount(1234L);
        balanceTransaction.setCurrency("USD");
        balanceTransaction.setFee(1L);
        balanceTransaction.setId("TEST");
        balanceTransaction.setNet(1233L);
        balanceTransaction.setType("charge");
        balanceTransactionList.add(balanceTransaction);

        StripeGatewayImpl.SettlementInfo settlementInfo = new StripeGatewayImpl.SettlementInfo();
        settlementInfo.setChargeTransactions(balanceTransactionList);
        settlementInfo.setStripePayout(payout);

        LOG.info("Stripe mock in use. Mock payout returned.");
        return settlementInfo;
    }

    @Override
    public void updateCustomerPaymentMethod(String stripeCustomerId, String stripePaymentToken) {
        LOG.info("Stripe mock in use. Customer not updated.");
    }

    @Override
    public void sendPushNotification(String token, Notification notification) {
        LOG.info("FCM mock in use. No push sent.");
    }

    @Override
    public void sendEmailFromTemplate(String toAddress, String templateName, Map<String, String> templateData) {
        LOG.info("SES mock in use. No email sent.");
    }

    @Override
    public void sendInternalReport(String reportName, String bodyText, List<ReportAttachment> reports) {
        LOG.info("SES mock in use. No email sent.");
    }

    @Override
    public void resendUserVerificationEmail() {
        LOG.info("SES mock in use. No email sent.");
    }
}
