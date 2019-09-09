package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.UserEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface UserRepository extends CrudRepository<UserEntity, UUID> {

    UserEntity findByAuth0Id(String auth0Id);

    UserEntity findFirstByEmail(String email);
}
