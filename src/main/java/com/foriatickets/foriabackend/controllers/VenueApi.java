package com.foriatickets.foriabackend.controllers;

import io.swagger.model.Venue;
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
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class VenueApi implements io.swagger.api.VenueApi {

    @Override
    @RequestMapping(value = "/venue", method = RequestMethod.POST)
    public ResponseEntity<Venue> createVenue(@Valid @RequestBody Venue body) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequestMapping(value = "/venue/{venue_id}", method = RequestMethod.GET)
    public ResponseEntity<Venue> getVenue(@Size(max = 36) @PathVariable("venue_id") String venueId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
