package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.TicketEntity;
import com.foriatickets.foriabackend.entities.TransferRequestEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferRequestRepository extends CrudRepository<TransferRequestEntity, UUID> {

    TransferRequestEntity findFirstByTicketAndStatus(TicketEntity ticketEntity, TransferRequestEntity.Status status);

    List<TransferRequestEntity> findAllByReceiverEmailAndStatus(String receiverEmail, TransferRequestEntity.Status status);
}
