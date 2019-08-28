package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.DeviceTokenEntity;
import com.foriatickets.foriabackend.entities.UserEntity;
import com.foriatickets.foriabackend.repositories.DeviceTokenRepository;
import com.foriatickets.foriabackend.repositories.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.openapitools.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.transaction.Transactional;
import java.time.OffsetDateTime;

@Service
@Transactional
public class UserCreationServiceImpl implements UserCreationService {

    private static final Logger LOG = LogManager.getLogger();

    private final DeviceTokenRepository deviceTokenRepository;
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;

    @Autowired
    public UserCreationServiceImpl(DeviceTokenRepository deviceTokenRepository, ModelMapper modelMapper, UserRepository userRepository) {

        assert userRepository != null;
        this.deviceTokenRepository = deviceTokenRepository;
        this.modelMapper = modelMapper;
        this.userRepository = userRepository;
    }

    @Override
    public User createUser(User newUser) {

        UserEntity existingUser = userRepository.findByAuth0Id(newUser.getAuth0Id());
        if (existingUser != null) {

            LOG.warn("Found existing user with UserID: {} - auth0ID: {}", existingUser.getId(), newUser.getAuth0Id());
            newUser.setId(existingUser.getId());
            return newUser;
        }

        UserEntity userEntity = new UserEntity();
        userEntity
                .setAuth0Id(newUser.getAuth0Id())
                .setFirstName(newUser.getFirstName())
                .setLastName(newUser.getLastName())
                .setEmail(newUser.getEmail());

        userEntity = userRepository.save(userEntity);
        newUser.setId(userEntity.getId());

        LOG.info("Created new user with ID: {}", newUser.getId());
        return newUser;
    }

    @Override
    public User getUser() {

        //Load user from Auth0 token.
        String auth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        UserEntity authenticatedUser = userRepository.findByAuth0Id(auth0Id);
        if (authenticatedUser == null) {

            LOG.error("Attempted to complete checkout with non-mapped auth0Id: {}", auth0Id);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User must be created in Foria system.");
        }
        return modelMapper.map(authenticatedUser, User.class);
    }

    @Override
    public void registerDeviceToken(String deviceToken) {

        //Load user from Auth0 token.
        String auth0Id = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        UserEntity authenticatedUser = userRepository.findByAuth0Id(auth0Id);
        if (authenticatedUser == null) {

            LOG.error("Attempted to complete checkout with non-mapped auth0Id: {}", auth0Id);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User must be created in Foria system.");
        }

        DeviceTokenEntity entity = new DeviceTokenEntity();
        entity.setDeviceToken(deviceToken)
                .setCreatedDate(OffsetDateTime.now())
                .setTokenStatus(DeviceTokenEntity.TokenStatus.ACTIVE)
                .setUserEntity(authenticatedUser);

        entity = deviceTokenRepository.save(entity);
        LOG.info("Device token for userId: {} saved with ID: {}", authenticatedUser.getId(), entity.getId());
    }
}
