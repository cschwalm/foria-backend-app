package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.VenueEntity;
import com.foriatickets.foriabackend.repositories.VenueRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.openapitools.model.Venue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Scope(scopeName = SCOPE_REQUEST)
@Service
@Transactional
public class VenueServiceImpl implements VenueService {

    private static final Logger LOG = LogManager.getLogger();

    private ModelMapper modelMapper;

    private VenueRepository venueRepository;

    @Autowired
    public VenueServiceImpl(VenueRepository venueRepository, ModelMapper modelMapper) {

        this.modelMapper = modelMapper;
        this.venueRepository = venueRepository;
    }

    @Override
    public Venue createVenue(Venue venue) {

        VenueEntity venueEntity = modelMapper.map(venue, VenueEntity.class);

        venueEntity = venueRepository.save(venueEntity);
        venue.setId(venueEntity.getId());

        LOG.info("Created venue entry with ID: {}", venueEntity.getId());
        return venue;
    }

    @Override
    public Optional<Venue> getVenue(UUID venueId) {

        Optional<VenueEntity> venueEntityOptional = this.venueRepository.findById(venueId);
        if (!venueEntityOptional.isPresent()) {
            LOG.warn("Supplied venue ID {} does not exist.", venueId);
            return Optional.empty();
        }

        VenueEntity venueEntity = venueEntityOptional.get();
        return Optional.of(modelMapper.map(venueEntity, Venue.class));
    }
}
