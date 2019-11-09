package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.EventEntity;
import com.foriatickets.foriabackend.entities.UserEntity;
import com.foriatickets.foriabackend.entities.VenueAccessEntity;
import com.foriatickets.foriabackend.entities.VenueEntity;
import com.foriatickets.foriabackend.repositories.UserRepository;
import com.foriatickets.foriabackend.repositories.VenueAccessRepository;
import com.foriatickets.foriabackend.repositories.VenueRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.openapitools.model.Event;
import org.openapitools.model.Venue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Scope(scopeName = SCOPE_REQUEST)
@Service
@Transactional
public class VenueServiceImpl implements VenueService {

    private static final Logger LOG = LogManager.getLogger();

    private final UserEntity authenticatedUser;

    private final ModelMapper modelMapper;

    private final UserRepository userRepository;

    private final VenueAccessRepository venueAccessRepository;

    private final VenueRepository venueRepository;

    @Autowired
    public VenueServiceImpl(UserRepository userRepository, VenueAccessRepository venueAccessRepository, VenueRepository venueRepository, ModelMapper modelMapper) {

        this.modelMapper = modelMapper;
        this.userRepository = userRepository;
        this.venueAccessRepository = venueAccessRepository;
        this.venueRepository = venueRepository;

        //Load user from Auth0 token.
        String auth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        authenticatedUser = userRepository.findByAuth0Id(auth0Id);
        if (authenticatedUser == null && !auth0Id.equalsIgnoreCase("anonymousUser") && !auth0Id.equalsIgnoreCase("auth0")) {
            LOG.warn("Attempted to create venue service with non-mapped auth0Id: {}", auth0Id);
        }
    }

    @Override
    public void authorizeUser(UUID venueId, UUID userId) {

        if (userId == null || venueId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID set must not be null.");
        }

        final boolean doesAlreadyExist = venueAccessRepository.existsByVenueEntity_IdAndUserEntity_Id(venueId, userId);
        if (doesAlreadyExist) {
            LOG.info("User ID: {} is already authorized for venue ID: {}", userId, venueId);
            return;
        }

        final Optional<UserEntity> userEntity = userRepository.findById(userId);
        final Optional<VenueEntity> venueEntity = venueRepository.findById(venueId);

        if (!userEntity.isPresent() || !venueEntity.isPresent()) {
            LOG.info("Venue or user not found by ID.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue or user not found by ID.");
        }

        VenueAccessEntity venueAccessEntity = new VenueAccessEntity();
        venueAccessEntity.setCreatedDate(OffsetDateTime.now());
        venueAccessEntity.setUserEntity(userEntity.get());
        venueAccessEntity.setVenueEntity(venueEntity.get());

        venueAccessRepository.save(venueAccessEntity);
        LOG.info("User ID: {} authorized for Venue ID: {}", userId, venueId);
    }

    @Override
    public void deauthorizeUser(UUID venueId, UUID userId) {

        if (userId == null || venueId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID set must not be null.");
        }

        final boolean doesAlreadyExist = venueAccessRepository.existsByVenueEntity_IdAndUserEntity_Id(venueId, userId);
        if (!doesAlreadyExist) {
            LOG.info("User ID: {} is not authorized for venue ID: {}", userId, venueId);
            return;
        }

        final VenueAccessEntity venueAccessEntity = venueAccessRepository
                .findByVenueEntity_IdAndUserEntity_Id(venueId, userId);

        if (venueAccessEntity == null) {
            LOG.info("User ID: {} is not authorized for venue ID: {}", userId, venueId);
            return;
        }

        venueAccessRepository.delete(venueAccessEntity);
        LOG.info("User ID: {} deauthorized for Venue ID: {}", userId, venueId);
    }

    @Override
    public Venue createVenue(Venue venue) {

        if (venue.getId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue ID must not be set when creating a venue.");
        }

        VenueEntity venueEntity = modelMapper.map(venue, VenueEntity.class);

        venueEntity = venueRepository.save(venueEntity);
        venue.setId(venueEntity.getId());

        LOG.info("Created venue entry with ID: {}", venueEntity.getId());
        return venue;
    }

    @Override
    public List<Venue> getAllVenues() {

        List<Venue> venueList = new ArrayList<>();

        for (VenueAccessEntity venueAccessEntity : authenticatedUser.getVenueAccessEntities()) {

            Optional<Venue> venue = getVenue(venueAccessEntity.getVenueEntity().getId());
            venue.ifPresent(venueList::add);
        }

        LOG.info("Obtained {} venues for userID: {}", venueList.size(), authenticatedUser.getId());
        return venueList;
    }

    @Override
    public Optional<Venue> getVenue(UUID venueId) {

        Optional<VenueEntity> venueEntityOptional = this.venueRepository.findById(venueId);
        if (!venueEntityOptional.isPresent()) {
            LOG.warn("Supplied venue ID {} does not exist.", venueId);
            return Optional.empty();
        }

        VenueEntity venueEntity = venueEntityOptional.get();
        Venue venue = modelMapper.map(venueEntity, Venue.class);

        List<Event> eventList = new ArrayList<>();
        venue.setEvents(eventList);
        for (EventEntity eventEntity : venueEntity.getEvents()) {

            Event event = modelMapper.map(eventEntity, Event.class);
            EventServiceImpl.populateEventModelWithAddress(event, venueEntity);
            eventList.add(event);
        }

        return Optional.of(venue);
    }
}
