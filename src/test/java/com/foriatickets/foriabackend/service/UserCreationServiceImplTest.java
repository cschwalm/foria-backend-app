package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.entities.UserEntity;
import com.foriatickets.foriabackend.repositories.UserRepository;
import io.swagger.model.User;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class UserCreationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserCreationService userCreationService;

    @Before
    public void setUp() {
        userCreationService = new UserCreationServieImpl(userRepository);
    }

    @Test
    public void createUser() {

        UserEntity userEntityMock = new UserEntity();
        userEntityMock.setEmail("test@test.com");
        userEntityMock.setFirstName("Test");
        userEntityMock.setLastName("Test");
        userEntityMock.setAuth0Id("auth0|test");
        userEntityMock.setId(UUID.randomUUID());

        User userMock = new User();
        userMock.setEmail("test@test.com");
        userMock.setFirstName("Test");
        userMock.setLastName("Test");
        userMock.setAuth0Id("auth0|test");

        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntityMock);

        User actual = userCreationService.createUser(userMock);
        Assert.assertEquals(userMock.getAuth0Id(), actual.getAuth0Id());
        Assert.assertEquals(userMock.getFirstName(), actual.getFirstName());
        Assert.assertEquals(userMock.getLastName(), actual.getLastName());
        Assert.assertEquals(userMock.getEmail(), actual.getEmail());
        Assert.assertNotNull(actual.getId());
    }
}