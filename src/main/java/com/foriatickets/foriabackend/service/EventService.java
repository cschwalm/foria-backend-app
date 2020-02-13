package com.foriatickets.foriabackend.service;

import org.openapitools.model.*;

import java.util.List;
import java.util.UUID;

/**
 * Event service to preform venue management.
 *
 * @author Corbin Schwalm
 */
public interface EventService {

    /**
     * Obtains the list of public type configs with the hidden promo tier.
     *
     * @param eventId The event
     * @param promoCode User supplied promo code. May be empty.
     */
    List<TicketTypeConfig> applyPromotionCode(UUID eventId, String promoCode);

    /**
     * Irrevocably CANCELS an event. This takes down the listing, CANCELS ALL orders, CANCELS ALL tickets,
     * REFUNDS ALL customers, and notifies users the event is CANCELED by push and email. Use with extreme caution.
     *
     * @param eventID The event to cancel.
     * @param reason The reason to show to user.
     */
    void cancelEvent(UUID eventID, String reason);

    /**
     * Create an event.
     *
     * @param event Event to created.
     * @return Event object populated with UUID.
     */
    Event createEvent(Event event);

    /**
     * On-boards a new promo code for the specified tier.
     *
     * @param promotionCodeCreateRequest new code
     */
    void createPromotionCode(PromotionCodeCreateRequest promotionCodeCreateRequest);

    /**
     * Creates a new fee entry for the event.
     *
     * @param eventId Event ID
     * @param ticketFeeConfig New fee config.
     * @return Newly created model.
     */
    TicketFeeConfig createTicketFeeConfig(UUID eventId, TicketFeeConfig ticketFeeConfig);

    /**
     * Creates a new price tier for an existing event.
     *
     * @param eventId Event ID
     * @param ticketTypeConfig New tier to create.
     * @return Newly created model.
     */
    TicketTypeConfig createTicketTypeConfig(UUID eventId, TicketTypeConfig ticketTypeConfig);

    /**
     * Returns a list of events that are currently running sorted by their event start date.
     *
     * @return A sorted list of events.
     */
    List<Event> getAllActiveEvents();

    /**
     * Obtains all ticket metadata that has been issued for the event.
     *
     * @param eventId Event to search.
     * @return A list of ticket owners that have not been canceled.
     */
    List<Attendee> getAttendees(UUID eventId);

    /**
     * Returns the venue scoped at request.
     * @param eventId id
     * @return Scoped venue.
     */
    Event getEvent(UUID eventId);

    /**
     * Prevents the fee from being applied to future ticket orders.
     *
     * @param eventId Event ID.
     * @param ticketFeeConfigId id
     * @return Removed fee config
     */
    TicketFeeConfig removeTicketFeeConfig(UUID eventId, UUID ticketFeeConfigId);

    /**
     * Prevents the fee from being applied to future ticket orders.
     *
     * @param eventId event id
     * @param ticketTypeConfigId id
     * @return Removed price tier.
     */
    TicketTypeConfig removeTicketTypeConfig(UUID eventId, UUID ticketTypeConfigId);

    /**
     * Allows the user to update an event's basic metadata.
     * Separate endpoints are required for price/fee config.
     *
     * @param eventId The existing event to update.
     * @param updatedEvent New data to use.
     * @return The updated Event.
     */
    Event updateEvent(UUID eventId, Event updatedEvent);
}
