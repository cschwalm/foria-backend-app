package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.OrderEntity;
import org.springframework.data.repository.CrudRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends CrudRepository<OrderEntity, UUID> {

    List<OrderEntity> findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBeforeOrderByOrderTimestampAsc(OffsetDateTime startTime, OffsetDateTime endTime);

    OrderEntity findByChargeReferenceId(String chargeReferenceId);
    OrderEntity findByRefundReferenceId(String refundReferenceId);
}
