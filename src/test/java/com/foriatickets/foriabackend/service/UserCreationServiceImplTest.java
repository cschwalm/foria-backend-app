package com.foriatickets.foriabackend.service;

import com.foriatickets.foriabackend.config.BeanConfig;
import com.foriatickets.foriabackend.entities.UserEntity;
import com.foriatickets.foriabackend.gateway.AWSSimpleEmailServiceGateway;
import com.foriatickets.foriabackend.repositories.DeviceTokenRepository;
import com.foriatickets.foriabackend.repositories.UserRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.openapitools.model.User;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class UserCreationServiceImplTest {

    private static final String ACCOUNT_CREATION_FAIL_EMAIL_TEMPLATE = "account_creation_error";

    @Mock
    private AWSSimpleEmailServiceGateway awsSimpleEmailServiceGateway;

    @Mock
    private BeanFactory beanFactory;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private TicketService ticketService;

    @Mock
    private UserRepository userRepository;

    private UserCreationService userCreationService;

    @Before
    public void setUp() {

        ModelMapper modelMapper = new ModelMapper();
        for (PropertyMap map : BeanConfig.getModelMappers()) {
            //noinspection unchecked
            modelMapper.addMappings(map);
        }

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("test");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        doNothing().when(ticketService).checkAndConfirmPendingTicketTransfers(any());
        when(beanFactory.getBean(TicketService.class)).thenReturn(ticketService);

        userCreationService = new UserCreationServiceImpl(awsSimpleEmailServiceGateway, beanFactory, deviceTokenRepository, modelMapper, userRepository);
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

        when(userRepository.findFirstByEmail(eq(userEntityMock.getEmail()))).thenReturn(null);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntityMock);

        User actual = userCreationService.createUser(userMock);
        Assert.assertEquals(userMock.getAuth0Id(), actual.getAuth0Id());
        Assert.assertEquals(userMock.getFirstName(), actual.getFirstName());
        Assert.assertEquals(userMock.getLastName(), actual.getLastName());
        Assert.assertEquals(userMock.getEmail(), actual.getEmail());
        Assert.assertNotNull(actual.getId());
    }

    @Test(expected = ResponseStatusException.class)
    public void createUser_DupEmail() {

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

        when(userRepository.findFirstByEmail(eq(userEntityMock.getEmail()))).thenReturn(userEntityMock);
        doNothing().when(awsSimpleEmailServiceGateway).sendEmailFromTemplate(any(), any(), any());

        try {
            userCreationService.createUser(userMock);
        } catch (Exception ex) {
            verify(awsSimpleEmailServiceGateway).sendEmailFromTemplate(eq(userEntityMock.getEmail()), eq(ACCOUNT_CREATION_FAIL_EMAIL_TEMPLATE), eq(null));
            throw ex;
        }
    }

    @Test
    public void getUser() {

        UserEntity userEntityMock = mock(UserEntity.class);
        when(userEntityMock.getId()).thenReturn(UUID.randomUUID());
        when(userEntityMock.getStripeId()).thenReturn("stripe");
        when(userEntityMock.getFirstName()).thenReturn("John");
        when(userEntityMock.getLastName()).thenReturn("Doe");
        when(userEntityMock.getEmail()).thenReturn("john.doe@test.com");

        when(userRepository.findByAuth0Id(any())).thenReturn(userEntityMock);

        User actual = userCreationService.getUser();
        Assert.assertNotNull(actual);

        verify(userRepository).findByAuth0Id(any());
    }
}