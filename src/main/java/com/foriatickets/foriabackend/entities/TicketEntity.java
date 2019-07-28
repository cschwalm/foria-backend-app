package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ticket")
@SuppressWarnings("unused")
public class TicketEntity implements Serializable {

    public enum Status {
        ISSUED,
        ACTIVE,
        TRANSFER_PENDING,
        REDEEMED,
        CANCELED,
        CANCELED_FRAUD
    }

    private UUID id;
    private EventEntity eventEntity;
    private UserEntity ownerEntity;
    private UserEntity purchaserEntity;
    private TicketTypeConfigEntity ticketTypeConfigEntity;
    private String secret;
    private Status status;
    private OffsetDateTime issuedDate;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public TicketEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    public EventEntity getEventEntity() {
        return eventEntity;
    }

    public TicketEntity setEventEntity(EventEntity eventEntity) {
        this.eventEntity = eventEntity;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    public UserEntity getOwnerEntity() {
        return ownerEntity;
    }

    public TicketEntity setOwnerEntity(UserEntity ownerEntity) {
        this.ownerEntity = ownerEntity;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchaser_id", nullable = false)
    public UserEntity getPurchaserEntity() {
        return purchaserEntity;
    }

    public TicketEntity setPurchaserEntity(UserEntity purchaserEntity) {
        this.purchaserEntity = purchaserEntity;
        return this;
    }

    @Column(name = "secret", nullable = false)
    public String getSecret() {
        return secret;
    }

    public TicketEntity setSecret(String secret) {
        this.secret = secret;
        return this;
    }

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    public Status getStatus() {
        return status;
    }

    public TicketEntity setStatus(Status status) {
        this.status = status;
        return this;
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticket_type_config_id", nullable = false)
    public TicketTypeConfigEntity getTicketTypeConfigEntity() {
        return ticketTypeConfigEntity;
    }

    public TicketEntity setTicketTypeConfigEntity(TicketTypeConfigEntity ticketTypeConfigEntity) {
        this.ticketTypeConfigEntity = ticketTypeConfigEntity;
        return this;
    }

    @Column(name = "issued_date", nullable = false)
    public OffsetDateTime getIssuedDate() {
        return issuedDate;
    }

    public TicketEntity setIssuedDate(OffsetDateTime issuedDate) {
        this.issuedDate = issuedDate;
        return this;
    }

    @Override
    public String toString() {
        return "TicketEntity{" +
                "key=" + id +
                ", eventEntityId=" + eventEntity.getId() +
                ", ownerEntityId=" + ownerEntity.getId() +
                ", status=" + status +
                ", ticketTypeConfigEntity=" + ticketTypeConfigEntity +
                ", issuedDate=" + issuedDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TicketEntity that = (TicketEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(secret, that.secret) &&
                status == that.status &&
                Objects.equals(issuedDate, that.issuedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, secret, status, issuedDate);
    }
}
