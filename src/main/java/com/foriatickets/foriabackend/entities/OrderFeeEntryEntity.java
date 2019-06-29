package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;
import org.openapitools.model.TicketFeeConfig;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_fee_entry")
@SuppressWarnings("unused")
public class OrderFeeEntryEntity implements Serializable {

    private UUID id;
    private OrderEntity orderEntity;
    private TicketFeeConfigEntity ticketFeeConfigEntity;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public OrderFeeEntryEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    public OrderEntity getOrderEntity() {
        return orderEntity;
    }

    public OrderFeeEntryEntity setOrderEntity(OrderEntity orderEntity) {
        this.orderEntity = orderEntity;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_fee_config_id", nullable = false)
    public TicketFeeConfigEntity getTicketFeeConfigEntity() {
        return ticketFeeConfigEntity;
    }

    public OrderFeeEntryEntity setTicketFeeConfigEntity(TicketFeeConfigEntity ticketFeeConfigEntity) {
        this.ticketFeeConfigEntity = ticketFeeConfigEntity;
        return this;
    }

    @Override
    public String toString() {
        return "OrderTicketEntryEntity{" +
                "id=" + id +
                ", orderEntity=" + orderEntity +
                ", ticketEntity=" + ticketFeeConfigEntity +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderFeeEntryEntity that = (OrderFeeEntryEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
