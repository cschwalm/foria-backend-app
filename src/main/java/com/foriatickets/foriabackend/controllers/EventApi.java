package com.foriatickets.foriabackend.controllers;

import com.foriatickets.foriabackend.service.EventService;
import org.openapitools.model.Attendee;
import org.openapitools.model.BaseApiModel;
import org.openapitools.model.CancelEvent;
import org.openapitools.model.Event;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class EventApi implements org.openapitools.api.EventApi {

    private final BeanFactory beanFactory;

    public EventApi(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @RequestMapping(value = "/event", method = RequestMethod.GET)
    @Override
    public ResponseEntity<List<Event>> getAllEvents() {
        EventService eventService = beanFactory.getBean(EventService.class);
        return new ResponseEntity<>(eventService.getAllActiveEvents(), HttpStatus.OK);
    }

    @RequestMapping(value = "/event", method = RequestMethod.POST)
    @Override
    public ResponseEntity<Event> createEvent(@Valid @RequestBody Event body) {

        EventService eventService = beanFactory.getBean(EventService.class);
        Event response = eventService.createEvent(body);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/event/{event_id}", method = RequestMethod.GET)
    @Override
    public ResponseEntity<Event> getEvent(@PathVariable("event_id") UUID eventId) {

        EventService eventService = beanFactory.getBean(EventService.class);
        return new ResponseEntity<>(eventService.getEvent(eventId), HttpStatus.OK);
    }

    @RequestMapping(value = "/event/{event_id}", method = RequestMethod.PUT)
    @Override
    public ResponseEntity<Event> updateEvent(@PathVariable("event_id") UUID eventId, @Valid Event event) {

        EventService eventService = beanFactory.getBean(EventService.class);
        return new ResponseEntity<>(eventService.updateEvent(eventId, event), HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/event/{event_id}/attendees", method = RequestMethod.GET)
    public ResponseEntity<List<Attendee>> getAttendeesForEvent(@PathVariable("event_id") UUID eventId) {
        EventService eventService = beanFactory.getBean(EventService.class);
        return new ResponseEntity<>(eventService.getAttendees(eventId), HttpStatus.OK);
    }

    @RequestMapping(value = "/event/{event_id}/cancel", method = RequestMethod.PUT)
    @Override
    public ResponseEntity<BaseApiModel> cancelEvent(@PathVariable("event_id") UUID eventId, @Valid @RequestBody CancelEvent cancelEvent) {

        EventService eventService = beanFactory.getBean(EventService.class);
        eventService.cancelEvent(eventId, cancelEvent.getReason());
        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }
}
