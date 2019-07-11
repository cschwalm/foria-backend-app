package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.OrderFeeEntryEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface OrderFeeEntryRepository extends CrudRepository<OrderFeeEntryEntity, UUID> {
}
