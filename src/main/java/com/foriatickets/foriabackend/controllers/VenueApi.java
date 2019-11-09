package com.foriatickets.foriabackend.controllers;

import com.foriatickets.foriabackend.service.VenueService;
import org.openapitools.model.BaseApiModel;
import org.openapitools.model.Venue;
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
public class VenueApi implements org.openapitools.api.VenueApi {

    private final BeanFactory beanFactory;

    public VenueApi(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    @RequestMapping(value = "/venue", method = RequestMethod.GET)
    public ResponseEntity<List<Venue>> getAllVenues() {
        VenueService venueService = beanFactory.getBean(VenueService.class);
        List<Venue> venueList = venueService.getAllVenues();
        return new ResponseEntity<>(venueList, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/venue", method = RequestMethod.POST)
    public ResponseEntity<Venue> createVenue(@Valid @RequestBody Venue body) {

        VenueService venueService = beanFactory.getBean(VenueService.class);
        Venue response = venueService.createVenue(body);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/venue/{venue_id}", method = RequestMethod.GET)
    public ResponseEntity<Venue> getVenue(@PathVariable("venue_id") UUID venueId) {

        VenueService venueService = beanFactory.getBean(VenueService.class);
        return ResponseEntity.of(venueService.getVenue(venueId));
    }

    @Override
    @RequestMapping(value = "/venue/{venue_id}/authorize/{user_id}", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> authorizeUser(@PathVariable("venue_id")UUID venueId, @PathVariable("user_id")UUID userId) {

        VenueService venueService = beanFactory.getBean(VenueService.class);
        venueService.authorizeUser(venueId, userId);
        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/venue/{venue_id}/deauthorize/{user_id}", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> deauthorizeUser(@PathVariable("venue_id") UUID venueId, @PathVariable("user_id") UUID userId) {

        VenueService venueService = beanFactory.getBean(VenueService.class);
        venueService.deauthorizeUser(venueId, userId);
        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }
}
