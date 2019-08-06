package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.TicketEntity;
import org.openapitools.model.*;

import java.util.List;
import java.util.UUID;

/**
 * Service that allows bulk operations on tickets.
 *
 * @author Corbin Schwalm
 */
public interface TicketService {

    /**
     * Activates a ticket for a user. This allows the ticket secret to be saved securely on user device.
     * Reactivate must be called to change devices.
     *
     * @param ticketId Ticket to activate.
     * @return Result object.
     */
    ActivationResult activateTicket(UUID ticketId);

    /**
     * Calculates the order total to display to the user.
     * This uses the same logic that checkout uses to ensure the price is the same.
     *
     * @param eventId eventId
     * @param orderConfig List of tickets to buy.
     */
    OrderTotal calculateOrderTotal(UUID eventId, List<TicketLineItem> orderConfig);

    /**
     * Accepts checkout data from client and completes transaction.
     * Transaction may fail if the token supplied contained invalid card data.
     *
     * @param paymentToken Token to retrieve payment data and attach it to user.
     * @param orderConfig List of tickets to buy.
     * @return Returns the UUID of the created order.
     */
    UUID checkoutOrder(String paymentToken, UUID eventId, List<TicketLineItem> orderConfig);

    /**
     * Returns the amount of tickets for type bucket capped at the ticket order max.
     *
     * @param ticketTypeConfigId id
     * @return Non-negative number not to exceed order max.
     */
    int countTicketsRemaining(UUID ticketTypeConfigId);

    /**
     * Obtains information about the specified ticket.
     * @param ticketId id
     * @return Ticket info minus secret.
     */
    Ticket getTicket(UUID ticketId);

    /**
     * Returns the tickets the currently logged in user owns.
     *
     * @return A list of tickets without secrets.
     */
    List<Ticket> getUsersTickets();

    /**
     * Creates (issues) a ticket by creating the entry, authorizing it for the purchasing user, and generating a ticket
     * secret.
     *
     * @param purchaserId The id of the purchaser. Because owner.
     * @param eventId The event to issue ticket for.
     * @param ticketTypeId Ticket type.
     * @return An issued ticket.
     */
    TicketEntity issueTicket(UUID purchaserId, UUID eventId, UUID ticketTypeId);

    /**
     * Allows user to allow ticket to be displayed on a new device.
     * This preforms verification checks, generates and saves a new ticket secret, and returns.
     *
     * @param ticketId Ticket to reactivate. Old device won't be able to use.
     * @return API result.
     */
    ActivationResult reactivateTicket(UUID ticketId);

    /**
     * Attempts to redeem the ticket via the specified ID and ticket secret.
     *
     * @param ticketId Ticket to redeem.
     * @param otpCode OTP code supplied by client device.
     * @return API result.
     */
    RedemptionResult redeemTicket(UUID ticketId, String otpCode);


}
