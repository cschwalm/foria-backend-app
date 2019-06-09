package com.foriatickets.foriabackend.controllers;

import io.swagger.model.Event;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import javax.validation.constraints.Size;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class EventApi implements io.swagger.api.EventApi {

    @RequestMapping(value = "/event", method = RequestMethod.POST)
    @Override
    public ResponseEntity<Event> createEvent(@Valid @RequestBody Event body) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/event/{event_id}", method = RequestMethod.GET)
    @Override
    public ResponseEntity<Event> getEvent(@Size(max = 36) @PathVariable("event_id") String eventId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
