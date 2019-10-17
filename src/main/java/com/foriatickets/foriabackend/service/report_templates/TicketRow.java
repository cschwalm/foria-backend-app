package com.foriatickets.foriabackend.service.report_templates;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;

/**
 * POJO to write ticket data to CSV.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class TicketRow {

    @CsvBindByPosition(position = 0)
    @CsvBindByName(column = "Ticket Id", required = true)
    private String ticketId;

    @CsvBindByPosition(position = 1)
    @CsvBindByName(column = "Event Id", required = true)
    private String eventId;

    @CsvBindByPosition(position = 2)
    @CsvBindByName(column = "Event Name", required = true)
    private String eventName;

    @CsvBindByPosition(position = 3)
    @CsvBindByName(column = "Venue Id", required = true)
    private String venueId;

    @CsvBindByPosition(position = 4)
    @CsvBindByName(column = "Purchaser User Id", required = true)
    private String userId;

    @CsvBindByPosition(position = 5)
    @CsvBindByName(column = "Order Id", required = true)
    private String orderId;

    @CsvBindByPosition(position = 6)
    @CsvBindByName(column = "Issue Date", required = true)
    private String issueDateTime;

    @CsvBindByPosition(position = 7)
    @CsvBindByName(column = "Ticket Type Id", required = true)
    private String ticketConfigId;

    @CsvBindByPosition(position = 8)
    @CsvBindByName(column = "Ticket Type Name", required = true)
    private String ticketConfigName;

    @CsvBindByPosition(position = 9)
    @CsvBindByName(column = "Ticket Type Price", required = true)
    private String ticketConfigPrice;

    @CsvBindByPosition(position = 10)
    @CsvBindByName(column = "Ticket Type Currency", required = true)
    private String ticketConfigCurrency;

    @CsvBindByPosition(position = 11)
    @CsvBindByName(column = "Ticket Status", required = true)
    private String ticketStatus;

    public String getTicketId() {
        return ticketId;
    }

    public TicketRow setTicketId(String ticketId) {
        this.ticketId = ticketId;
        return this;
    }

    public String getEventId() {
        return eventId;
    }

    public TicketRow setEventId(String eventId) {
        this.eventId = eventId;
        return this;
    }

    public String getEventName() {
        return eventName;
    }

    public TicketRow setEventName(String eventName) {
        this.eventName = eventName;
        return this;
    }

    public String getVenueId() {
        return venueId;
    }

    public TicketRow setVenueId(String venueId) {
        this.venueId = venueId;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public TicketRow setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getOrderId() {
        return orderId;
    }

    public TicketRow setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public String getIssueDateTime() {
        return issueDateTime;
    }

    public TicketRow setIssueDateTime(String issueDateTime) {
        this.issueDateTime = issueDateTime;
        return this;
    }

    public String getTicketConfigId() {
        return ticketConfigId;
    }

    public TicketRow setTicketConfigId(String ticketConfigId) {
        this.ticketConfigId = ticketConfigId;
        return this;
    }

    public String getTicketConfigName() {
        return ticketConfigName;
    }

    public TicketRow setTicketConfigName(String ticketConfigName) {
        this.ticketConfigName = ticketConfigName;
        return this;
    }

    public String getTicketConfigPrice() {
        return ticketConfigPrice;
    }

    public TicketRow setTicketConfigPrice(String ticketConfigPrice) {
        this.ticketConfigPrice = ticketConfigPrice;
        return this;
    }

    public String getTicketConfigCurrency() {
        return ticketConfigCurrency;
    }

    public TicketRow setTicketConfigCurrency(String ticketConfigCurrency) {
        this.ticketConfigCurrency = ticketConfigCurrency;
        return this;
    }

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }
}
