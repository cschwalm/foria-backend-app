package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.UserEntity;
import com.foriatickets.foriabackend.repositories.UserRepository;
import io.swagger.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@Transactional
public class UserCreationServiceImpl implements UserCreationService {

    private static final Logger LOG = LogManager.getLogger();

    private UserRepository userRepository;

    public UserCreationServiceImpl(@Autowired UserRepository userRepository) {

        assert userRepository != null;
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
}
