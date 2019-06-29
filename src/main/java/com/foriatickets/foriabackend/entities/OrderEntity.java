package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "order")
@SuppressWarnings("unused")
public class OrderEntity implements Serializable {

    private UUID id;
    private UserEntity purchaser;
    private String chargeReferenceId;
    private OffsetDateTime orderTimestamp;
    private BigDecimal total;
    private String currency;
    private Set<OrderTicketEntryEntity> tickets;
    private Set<OrderFeeEntryEntity> fees;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public OrderEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchaser_id", nullable = false)
    public UserEntity getPurchaser() {
        return purchaser;
    }

    public OrderEntity setPurchaser(UserEntity purchaser) {
        this.purchaser = purchaser;
        return this;
    }

    @Column(name = "charge_ref_id", nullable = false)
    public String getChargeReferenceId() {
        return chargeReferenceId;
    }

    public OrderEntity setChargeReferenceId(String chargeReferenceId) {
        this.chargeReferenceId = chargeReferenceId;
        return this;
    }

    @Column(name = "timestamp", nullable = false)
    public OffsetDateTime getOrderTimestamp() {
        return orderTimestamp;
    }

    public OrderEntity setOrderTimestamp(OffsetDateTime orderTimestamp) {
        this.orderTimestamp = orderTimestamp;
        return this;
    }

    @Column(name = "total", nullable = false, length = 8, precision = 2)
    public BigDecimal getTotal() {
        return total;
    }

    public OrderEntity setTotal(BigDecimal total) {
        this.total = total;
        return this;
    }

    @Column(name = "currency", nullable = false, length = 3)
    public String getCurrency() {
        return currency;
    }

    public OrderEntity setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    @OneToMany(mappedBy = "orderEntity", fetch = FetchType.LAZY)
    public Set<OrderTicketEntryEntity> getTickets() {
        return tickets;
    }

    public OrderEntity setTickets(Set<OrderTicketEntryEntity> tickets) {
        this.tickets = tickets;
        return this;
    }

    @OneToMany(mappedBy = "orderEntity", fetch = FetchType.LAZY)
    public Set<OrderFeeEntryEntity> getFees() {
        return fees;
    }

    public OrderEntity setFees(Set<OrderFeeEntryEntity> fees) {
        this.fees = fees;
        return this;
    }

    @Override
    public String toString() {
        return "OrderEntity{" +
                "id=" + id +
                ", purchaser=" + purchaser +
                ", chargeReferenceId='" + chargeReferenceId + '\'' +
                ", orderTimestamp=" + orderTimestamp +
                ", total=" + total +
                ", currency='" + currency + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderEntity that = (OrderEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(chargeReferenceId, that.chargeReferenceId) &&
                Objects.equals(orderTimestamp, that.orderTimestamp) &&
                Objects.equals(total, that.total) &&
                Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, chargeReferenceId, orderTimestamp, total, currency);
    }
}
