package com.foriatickets.foriabackend.controllers;

import com.foriatickets.foriabackend.service.TicketService;
import org.openapitools.model.BaseApiModel;
import org.openapitools.model.Order;
import org.openapitools.model.Ticket;
import org.openapitools.model.TransferRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.util.UUID;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class TicketApi implements org.openapitools.api.TicketApi {

    private final TicketService ticketService;

    public TicketApi(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    @RequestMapping(value = "/ticket/checkout", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> checkout(@Valid Order order) {

        String auth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ticketService.checkoutOrder(auth0Id, order.getPaymentToken(), order.getEventId(), order.getTicketLineItemList());
        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/activate", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> activateTicket(@PathVariable("ticket_id") UUID ticketId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/cancelTransfer", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> cancelTransfer(@PathVariable("ticket_id") UUID ticketId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}", method = RequestMethod.GET)
    public ResponseEntity<Ticket> getTicket(@PathVariable("ticket_id") UUID ticketId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/reactivate", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> reactivateTicket(@PathVariable("ticket_id") UUID ticketId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/redeem", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> redeemTicket(@PathVariable("ticket_id") UUID ticketId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/ticket/{ticket_id}/transfer", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> transferTicket(@PathVariable("ticket_id") UUID ticketId, @Valid @RequestBody TransferRequest transferRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
