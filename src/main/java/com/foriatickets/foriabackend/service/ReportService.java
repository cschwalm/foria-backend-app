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
     * Generates report containing list of ticket sales for each venue
     * for the previous business day.
     */
    void generateAndSendDailyTicketPurchaseReport();

    /**
     * Gathers the list of Stripe transactions hitting bank account
     * and specifies what amount of money to send the venue on a
     * week period.
     */
    void generateAndSendWeeklySettlementReport();
}
