package com.foriatickets.foriabackend.service;

/**
 * Service builds CSV reports and emails them to Foria operations
 * to understand the number and type of tickets purchased for the
 * day.
 *
 * @author Corbin Schwalm
 */
public interface ReportService {

    /**
     * Generates a report that calculates the final event totals for an event.
     * This is to determine the amount to send the venue after settlement.
     */
    void generateAndSendEventEndReport();

    /**
     * Generates report containing list of ticket sales for each venue
     * for the previous business day.
     */
    void generateAndSendDailyTicketPurchaseReport();

    /**
     * Generates report containing list of ticket sales from now to all time.
     */
    void generateAndSendRollingTicketPurchaseReport();

    /**
     * Gathers the list of Stripe transactions hitting bank account
     * and specifies what amount of money to send the venue on a
     * week period.
     */
    void generateAndSendWeeklySettlementReport();

    /**
     * Runs daily to check and see if T-1 before an event.
     * Obtains the entire attendee list and sends each ticket owner an email.
     */
    void generateAndSendGeneralEventReminder();
}
