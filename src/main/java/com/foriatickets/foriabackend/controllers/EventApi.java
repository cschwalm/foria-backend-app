package com.foriatickets.foriabackend.controllers;

import com.foriatickets.foriabackend.service.EventService;
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
import java.util.UUID;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class EventApi implements org.openapitools.api.EventApi {

    private final BeanFactory beanFactory;

    public EventApi(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @RequestMapping(value = "/event", method = RequestMethod.POST)
    @Override
    public ResponseEntity<Event> createEvent(@Valid @RequestBody Event body) {

        EventService eventService = beanFactory.getBean(EventService.class, (Object) null);
        Event response = eventService.createEvent(body);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/event/{event_id}", method = RequestMethod.GET)
    @Override
    public ResponseEntity<Event> getEvent(@PathVariable("event_id") UUID eventId) {

        EventService eventService = beanFactory.getBean(EventService.class, eventId);
        return ResponseEntity.of(eventService.getEvent());
    }
}
