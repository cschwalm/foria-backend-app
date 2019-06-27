package com.foriatickets.foriabackend.service;

import io.swagger.model.Ticket;

import java.util.UUID;

/**
 * Service that allows bulk operations on tickets.
 *
 * @author Corbin Schwalm
 */
public interface TicketService {

    Ticket issueTicket(UUID purchaserId, UUID eventId, UUID ticketTypeId);
}
