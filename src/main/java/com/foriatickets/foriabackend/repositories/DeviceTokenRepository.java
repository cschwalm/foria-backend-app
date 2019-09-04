package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.DeviceTokenEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface DeviceTokenRepository extends CrudRepository<DeviceTokenEntity, UUID> {

    boolean existsByDeviceToken(String deviceToken);
}
