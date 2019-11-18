package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.OrderFeeEntryEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderFeeEntryRepository extends CrudRepository<OrderFeeEntryEntity, UUID> {
}
