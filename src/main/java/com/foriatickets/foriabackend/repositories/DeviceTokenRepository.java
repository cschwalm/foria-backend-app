package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.DeviceTokenEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends CrudRepository<DeviceTokenEntity, UUID> {

    boolean existsByDeviceToken(String deviceToken);
    DeviceTokenEntity findByDeviceToken(String deviceToken);
}
