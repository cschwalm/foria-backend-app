package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.UserEntity;
import com.foriatickets.foriabackend.entities.UserMusicInterestsEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserMusicInterestsRepository extends CrudRepository<UserMusicInterestsEntity, UUID> {

    UserMusicInterestsEntity findFirstByUserEntityOrderByProcessedDateDesc(UserEntity userEntity);
}
