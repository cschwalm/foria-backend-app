package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_ticket_entry")
@SuppressWarnings("unused")
public class OrderTicketEntryEntity implements Serializable {

    private UUID id;
    private OrderEntity orderEntity;
    private TicketEntity ticketEntity;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public OrderTicketEntryEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    public OrderEntity getOrderEntity() {
        return orderEntity;
    }

    public OrderTicketEntryEntity setOrderEntity(OrderEntity orderEntity) {
        this.orderEntity = orderEntity;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    public TicketEntity getTicketEntity() {
        return ticketEntity;
    }

    public OrderTicketEntryEntity setTicketEntity(TicketEntity ticketEntity) {
        this.ticketEntity = ticketEntity;
        return this;
    }

    @Override
    public String toString() {
        return "OrderTicketEntryEntity{" +
                "id=" + id +
                ", orderEntity=" + orderEntity +
                ", ticketEntity=" + ticketEntity +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderTicketEntryEntity that = (OrderTicketEntryEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
