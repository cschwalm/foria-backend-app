package com.foriatickets.foriabackend.controllers;

import com.foriatickets.foriabackend.gateway.Auth0Gateway;
import com.foriatickets.foriabackend.service.SpotifyIngestionService;
import com.foriatickets.foriabackend.service.TicketService;
import com.foriatickets.foriabackend.service.UserCreationService;
import org.openapitools.model.*;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.util.List;

@Controller
@RequestMapping(path = "/v1/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class UserApi implements org.openapitools.api.UserApi {

    private final BeanFactory beanFactory;

    public UserApi(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    @RequestMapping(value = "/user/tickets", method = RequestMethod.GET)
    public ResponseEntity<List<Ticket>> getTickets() {

        TicketService ticketService = beanFactory.getBean(TicketService.class);
        List<Ticket> tickets = ticketService.getUsersTickets();
        return new ResponseEntity<>(tickets, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/user", method = RequestMethod.GET)
    public ResponseEntity<User> getUser() {

        UserCreationService userCreationService = beanFactory.getBean(UserCreationService.class);
        User user = userCreationService.getUser();
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/user/music/topArtists", method = RequestMethod.POST)
    public ResponseEntity<UserTopArtists> getTopArtists(@Valid UserTopArtistsRequest userTopArtistsRequest) {

        final UserTopArtists userTopArtists;
        final SpotifyIngestionService spotifyIngestionService = beanFactory.getBean(SpotifyIngestionService.class);
        if (userTopArtistsRequest != null && userTopArtistsRequest.getPermalinkUuid() != null) {
            userTopArtists = spotifyIngestionService.processTopArtists(userTopArtistsRequest.getPermalinkUuid());
        } else {
            userTopArtists = spotifyIngestionService.processTopArtists(null);
        }

        return new ResponseEntity<>(userTopArtists, HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/user/linkAccounts", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> linkAccounts(@Valid AccountLinkRequest accountLinkRequest) {

        if (StringUtils.isEmpty(accountLinkRequest.getConnection()) || StringUtils.isEmpty(accountLinkRequest.getProvider())) {
            return new ResponseEntity<>(new BaseApiModel(), HttpStatus.BAD_REQUEST);
        }

        Auth0Gateway auth0Gateway = beanFactory.getBean(Auth0Gateway.class);
        auth0Gateway.linkAdditionalAccount(accountLinkRequest.getIdToken(), accountLinkRequest.getConnection(), accountLinkRequest.getProvider());
        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/user/unlinkAccounts", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> unlinkAccounts(@Valid AccountLinkRequest accountLinkRequest) {

        Auth0Gateway auth0Gateway = beanFactory.getBean(Auth0Gateway.class);
        auth0Gateway.unlinkAccountByConnection(accountLinkRequest.getConnection(), accountLinkRequest.getProvider());
        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/user/registerToken", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> registerToken(@Valid DeviceToken deviceToken) {

        UserCreationService userCreationService = beanFactory.getBean(UserCreationService.class);
        userCreationService.registerDeviceToken(deviceToken.getToken());
        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }

    @Override
    @RequestMapping(value = "/user/sendVerificationEmail", method = RequestMethod.POST)
    public ResponseEntity<BaseApiModel> sendVerificationEmail() {

        Auth0Gateway auth0Gateway = beanFactory.getBean(Auth0Gateway.class);
        auth0Gateway.resendUserVerificationEmail();
        return new ResponseEntity<>(new BaseApiModel(), HttpStatus.OK);
    }
}
