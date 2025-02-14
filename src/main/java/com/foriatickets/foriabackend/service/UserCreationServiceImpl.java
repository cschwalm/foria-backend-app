package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.DeviceTokenEntity;
import com.foriatickets.foriabackend.entities.UserEntity;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.repositories.DeviceTokenRepository;
import com.foriatickets.foriabackend.repositories.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.openapitools.model.User;
import org.springframework.beans.factory.BeanFactory;
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

    private static final String ACCOUNT_CREATION_FAIL_EMAIL_TEMPLATE = "account_creation_error";

    private static final Logger LOG = LogManager.getLogger();

    private final AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway;
    private final BeanFactory beanFactory;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;

    @Autowired
    public UserCreationServiceImpl(AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway, BeanFactory beanFactory, DeviceTokenRepository deviceTokenRepository, ModelMapper modelMapper, UserRepository userRepository) {

        assert userRepository != null;
        this.awsSimpleEmailServiceGateway = awsSimpleEmailServiceGateway;
        this.beanFactory = beanFactory;
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

        final UserEntity userEntityWithEmail = userRepository.findFirstByEmail(newUser.getEmail());
        if (userEntityWithEmail != null) {

            LOG.info("Email: {} attempted to signup but account already exists. Sending error email.", newUser.getEmail());
            awsSimpleEmailServiceGateway.sendEmailFromTemplate(newUser.getEmail(), ACCOUNT_CREATION_FAIL_EMAIL_TEMPLATE, null);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has already signed up with email: " + newUser.getEmail());
        }

        UserEntity userEntity = new UserEntity();
        userEntity
                .setAuth0Id(newUser.getAuth0Id())
                .setFirstName(newUser.getFirstName())
                .setLastName(newUser.getLastName())
                .setEmail(newUser.getEmail());

        userEntity = userRepository.save(userEntity);
        newUser.setId(userEntity.getId());

        //Transfer any pending tickets.
        TicketService ticketService = beanFactory.getBean(TicketService.class);
        ticketService.checkAndConfirmPendingTicketTransfers(userEntity);

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

            LOG.error("Attempted to register token with non-mapped auth0Id: {}", auth0Id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must be created in Foria system.");
        }

        if (deviceTokenRepository.existsByDeviceToken(deviceToken)) {
            return;
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
