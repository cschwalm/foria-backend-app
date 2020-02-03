package com.foriatickets.foriabackend.repositories;

import com.foriatickets.foriabackend.entities.PromoCodeEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface PromoCodeRepository extends CrudRepository<PromoCodeEntity, UUID> {

    PromoCodeEntity findByTicketTypeConfigEntity_EventEntity_IdAndCode(UUID eventId, String code);

    PromoCodeEntity findByTicketTypeConfigEntity_IdAndCode(UUID ticketTypeConfigId, String code);
}
