package com.foriatickets.foriabackend.entities;

import javax.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "promo_code_redemption")
@SuppressWarnings("unused")
public class PromoCodeRedemptionEntity implements Serializable {

    private UUID id;
    private PromoCodeEntity promoCodeEntity;
    private TicketTypeConfigEntity ticketTypeConfigEntity;
    private TicketEntity ticketEntity;
    private UserEntity userEntity;
    private OffsetDateTime redemptionDate;

    @Id
    @GeneratedValue
    @org.hibernate.annotations.Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_tier_promo_code_id")
    public PromoCodeEntity getPromoCodeEntity() {
        return promoCodeEntity;
    }

    public void setPromoCodeEntity(PromoCodeEntity promoCodeEntity) {
        this.promoCodeEntity = promoCodeEntity;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_config_id")
    public TicketTypeConfigEntity getTicketTypeConfigEntity() {
        return ticketTypeConfigEntity;
    }

    public void setTicketTypeConfigEntity(TicketTypeConfigEntity ticketTypeConfigEntity) {
        this.ticketTypeConfigEntity = ticketTypeConfigEntity;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    public TicketEntity getTicketEntity() {
        return ticketEntity;
    }

    public void setTicketEntity(TicketEntity ticketEntity) {
        this.ticketEntity = ticketEntity;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    public UserEntity getUserEntity() {
        return userEntity;
    }

    public void setUserEntity(UserEntity userEntity) {
        this.userEntity = userEntity;
    }

    @Column(name = "redemption_d", nullable = false)
    public OffsetDateTime getRedemptionDate() {
        return redemptionDate;
    }

    public void setRedemptionDate(OffsetDateTime redemptionDate) {
        this.redemptionDate = redemptionDate;
    }

    @Override
    public String toString() {
        return "PromoCodeRedemption{" +
                "id=" + id +
                ", redemptionDate=" + redemptionDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PromoCodeRedemptionEntity that = (PromoCodeRedemptionEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(redemptionDate, that.redemptionDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, redemptionDate);
    }
}
