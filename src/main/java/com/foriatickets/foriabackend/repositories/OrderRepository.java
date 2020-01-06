package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.OrderEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("SqlResolve")
@Repository
public interface OrderRepository extends CrudRepository<OrderEntity, UUID> {

    List<OrderEntity> findOrderEntitiesByOrderTimestampAfterAndOrderTimestampBeforeOrderByOrderTimestampAsc(OffsetDateTime startTime, OffsetDateTime endTime);

    OrderEntity findByChargeReferenceId(String chargeReferenceId);

    OrderEntity findByRefundReferenceId(String refundReferenceId);

    @Query(value =
            "SELECT DISTINCT o.* " +
            "FROM `order` AS o " +
            "INNER JOIN order_ticket_entry AS OTE ON OTE.order_id = o.id " +
            "INNER JOIN ticket AS t ON t.id = OTE.ticket_id " +
            "WHERE OTE.ticket_id IN ( " +
            "  SELECT id " +
            "  FROM `ticket` " +
            "  WHERE event_id = :eventId " +
            ")",
    nativeQuery = true)
    List<OrderEntity> findAllByEventId(@Param("eventId") String eventId);
}
