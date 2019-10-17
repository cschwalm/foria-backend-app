package com.foriatickets.foriabackend.service.report_templates;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;

/**
 * Report template used to generate CSV breaking down components of a charge.
 *
 * @author Corbin Schwalm
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class OrderReportRow {

    @CsvBindByPosition(position = 0)
    @CsvBindByName(column = "Order Id", required = true)
    private String orderId;

    @CsvBindByPosition(position = 1)
    @CsvBindByName(column = "Purchaser User Id", required = true)
    private String userId;

    @CsvBindByPosition(position = 2)
    @CsvBindByName(column = "Purchaser Email", required = true)
    private String userEmail;

    @CsvBindByPosition(position = 3)
    @CsvBindByName(column = "Order Date", required = true)
    private String orderDateTime;

    @CsvBindByPosition(position = 4)
    @CsvBindByName(column = "Charge Amount", required = true)
    private String chargeAmount;

    @CsvBindByPosition(position = 5)
    @CsvBindByName(column = "Payment Fee Amount", required = true)
    private String paymentFeeAmount;

    @CsvBindByPosition(position = 6)
    @CsvBindByName(column = "Net Amount", required = true)
    private String netAmount;

    @CsvBindByPosition(position = 7)
    @CsvBindByName(column = "Ticket Subtotal", required = true)
    private String ticketSubtotal;

    @CsvBindByPosition(position = 8)
    @CsvBindByName(column = "Issuer Fee Subtotal", required = true)
    private String issuerFeeAmount;

    @CsvBindByPosition(position = 9)
    @CsvBindByName(column = "Venue Fee Subtotal", required = true)
    private String venueFeeAmount;

    @CsvBindByPosition(position = 10)
    @CsvBindByName(column = "Currency", required = true)
    private String currency;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getOrderDateTime() {
        return orderDateTime;
    }

    public void setOrderDateTime(String orderDateTime) {
        this.orderDateTime = orderDateTime;
    }

    public String getChargeAmount() {
        return chargeAmount;
    }

    public void setChargeAmount(String chargeAmount) {
        this.chargeAmount = chargeAmount;
    }

    public String getPaymentFeeAmount() {
        return paymentFeeAmount;
    }

    public void setPaymentFeeAmount(String paymentFeeAmount) {
        this.paymentFeeAmount = paymentFeeAmount;
    }

    public String getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(String netAmount) {
        this.netAmount = netAmount;
    }

    public String getTicketSubtotal() {
        return ticketSubtotal;
    }

    public void setTicketSubtotal(String ticketSubtotal) {
        this.ticketSubtotal = ticketSubtotal;
    }

    public String getIssuerFeeAmount() {
        return issuerFeeAmount;
    }

    public void setIssuerFeeAmount(String issuerFeeAmount) {
        this.issuerFeeAmount = issuerFeeAmount;
    }

    public String getVenueFeeAmount() {
        return venueFeeAmount;
    }

    public void setVenueFeeAmount(String venueFeeAmount) {
        this.venueFeeAmount = venueFeeAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
