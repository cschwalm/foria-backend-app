package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ticket_fee_config")
@SuppressWarnings("unused")
public class TicketFeeConfigEntity implements Serializable {

    public enum FeeMethod {
        FLAT,
        PERCENT
    }

    public enum FeeType {
        ISSUER,
        VENUE
    }

    private UUID id;
    private EventEntity eventEntity;
    private String name;
    private String description;
    private FeeMethod method;
    private FeeType type;
    private BigDecimal amount;
    private String currency;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public TicketFeeConfigEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    public EventEntity getEventEntity() {
        return eventEntity;
    }

    public TicketFeeConfigEntity setEventEntity(EventEntity eventEntity) {
        this.eventEntity = eventEntity;
        return this;
    }

    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public TicketFeeConfigEntity setName(String name) {
        this.name = name;
        return this;
    }

    @Column(name = "description", nullable = false)
    public String getDescription() {
        return description;
    }

    public TicketFeeConfigEntity setDescription(String description) {
        this.description = description;
        return this;
    }

    @Column(name = "method", nullable = false)
    @Enumerated(EnumType.STRING)
    public FeeMethod getMethod() {
        return method;
    }

    public TicketFeeConfigEntity setMethod(FeeMethod method) {
        this.method = method;
        return this;
    }

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    public FeeType getType() {
        return type;
    }

    public TicketFeeConfigEntity setType(FeeType type) {
        this.type = type;
        return this;
    }

    @Column(name = "price", nullable = false, scale = 2, precision = 8)
    public BigDecimal getAmount() {
        return amount;
    }

    public TicketFeeConfigEntity setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    @Column(name = "currency", nullable = false, length = 3)
    public String getCurrency() {
        return currency;
    }

    public TicketFeeConfigEntity setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    @Override
    public String toString() {
        return "TicketFeeConfigEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", method=" + method +
                ", type=" + type +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TicketFeeConfigEntity that = (TicketFeeConfigEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                method == that.method &&
                type == that.type &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, method, type, amount, currency);
    }
}
