package com.foriatickets.foriabackend;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@Profile("local")
public class ForiaBackendApplicationIT {

    @Test
    public void contextLoads() {
        //Ensures that method doesn't throw exception.
    }
}
