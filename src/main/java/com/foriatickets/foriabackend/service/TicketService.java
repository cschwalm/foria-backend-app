package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.TicketEntity;
import org.openapitools.model.Ticket;
import org.openapitools.model.TicketLineItem;

import java.util.List;
import java.util.UUID;

/**
 * Service that allows bulk operations on tickets.
 *
 * @author Corbin Schwalm
 */
public interface TicketService {

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
     * Returns the amount of tickets for type bucket capped at the ticket order max.
     *
     * @param ticketTypeConfigId id
     * @return Non-negative number not to exceed order max.
     */
    int countTicketsRemaining(UUID ticketTypeConfigId);
}
