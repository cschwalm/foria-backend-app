package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ticket_type_config")
@SuppressWarnings("unused")
public class TicketTypeConfigEntity implements Serializable {

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    private UUID id;
    private EventEntity eventEntity;
    private String name;
    private String description;
    private Status status;
    private int authorizedAmount;
    private BigDecimal price;
    private String currency;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public TicketTypeConfigEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public TicketTypeConfigEntity setName(String name) {
        this.name = name;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    public EventEntity getEventEntity() {
        return eventEntity;
    }

    public TicketTypeConfigEntity setEventEntity(EventEntity eventEntity) {
        this.eventEntity = eventEntity;
        return this;
    }

    @Column(name = "description", nullable = false)
    public String getDescription() {
        return description;
    }

    public TicketTypeConfigEntity setDescription(String description) {
        this.description = description;
        return this;
    }

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Column(name = "authorized_amount", nullable = false)
    public int getAuthorizedAmount() {
        return authorizedAmount;
    }

    public TicketTypeConfigEntity setAuthorizedAmount(int authorizedAmount) {
        this.authorizedAmount = authorizedAmount;
        return this;
    }

    @Column(name = "price", nullable = false, scale = 2, precision = 8)
    public BigDecimal getPrice() {
        return price;
    }

    public TicketTypeConfigEntity setPrice(BigDecimal price) {
        this.price = price;
        return this;
    }

    @Column(name = "currency", nullable = false, length = 3)
    public String getCurrency() {
        return currency;
    }

    public TicketTypeConfigEntity setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    @Override
    public String toString() {
        return "TicketTypeConfigEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", authorizedAmount=" + authorizedAmount +
                ", price=" + price +
                ", currency='" + currency + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TicketTypeConfigEntity that = (TicketTypeConfigEntity) o;
        return authorizedAmount == that.authorizedAmount &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(status, that.status) &&
                Objects.equals(price, that.price) &&
                Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, status, authorizedAmount, price, currency);
    }
}
