package com.foriatickets.foriabackend;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles({"local", "mock"})
public class ForiaBackendApplicationIT {

    @Test
    public void contextLoads() {
        //Ensures that method doesn't throw exception.
    }
}
