package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.OrderEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface OrderRepository extends CrudRepository<OrderEntity, UUID> {
}
