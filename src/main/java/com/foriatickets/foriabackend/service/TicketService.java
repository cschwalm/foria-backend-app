package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.TicketEntity;
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
     * @param auth0Id ID of logged in user.
     * @param paymentToken Token to retrieve payment data and attach it to user.
     * @param orderConfig List of tickets to buy.
     * @return Returns the UUID of the created order.
     */
    UUID checkoutOrder(String auth0Id, String paymentToken, UUID eventId, List<TicketLineItem> orderConfig);

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
