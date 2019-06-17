package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.VenueEntity;
import com.foriatickets.foriabackend.repositories.VenueRepository;
import io.swagger.model.Venue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
public class VenueServiceImpl implements VenueService {

    private static final Logger LOG = LogManager.getLogger();

    private ModelMapper modelMapper;

    private VenueEntity venueEntity;
    private UUID venueId;
    private VenueRepository venueRepository;

    public VenueServiceImpl(UUID venueId, VenueRepository venueRepository) {

        modelMapper = new ModelMapper();
        PropertyMap<VenueEntity, Venue> venueDtoMap = new PropertyMap<VenueEntity, Venue>() {

            @Override
            protected void configure() {

                map().getAddress().setStreetAddress(source.getContactStreetAddress());
                map().getAddress().setCity(source.getContactCity());
                map().getAddress().setState(source.getContactState());
                map().getAddress().setZip(source.getContactZip());
                map().getAddress().setCountry(source.getContactCountry());
            }
        };

        PropertyMap<Venue, VenueEntity> venueEntityMap = new PropertyMap<Venue, VenueEntity>() {

            @Override
            protected void configure() {

                map().setContactStreetAddress(source.getAddress().getStreetAddress());
                map().setContactCity(source.getAddress().getCity());
                map().setContactState(source.getAddress().getState());
                map().setContactZip(source.getAddress().getZip());
                map().setContactCountry(source.getAddress().getCountry());
            }
        };

        modelMapper.addMappings(venueDtoMap);
        modelMapper.addMappings(venueEntityMap);

        this.venueId = venueId;
        this.venueRepository = venueRepository;

        if (venueId == null) {
            return;
        }

        Optional<VenueEntity> venueEntity = this.venueRepository.findById(venueId);
        if (!venueEntity.isPresent()) {
            LOG.warn("Supplied venue ID {} does not exist.", venueId);
            return;
        }

        this.venueEntity = venueEntity.get();
    }

    @Override
    public Venue createVenue(Venue venue) {

        VenueEntity venueEntity = modelMapper.map(venue, VenueEntity.class);

        this.venueEntity = venueRepository.save(venueEntity);
        venue.setId(venueEntity.getId());

        LOG.info("Created venue entry with ID: {}", venueEntity.getId());
        return venue;
    }

    @Override
    public Optional<Venue> getVenue() {

        if (venueEntity == null) {
            LOG.warn("Failed to return venue with ID: {}", venueId);
            return Optional.empty();
        }

        return Optional.of(modelMapper.map(venueEntity, Venue.class));
    }
}
