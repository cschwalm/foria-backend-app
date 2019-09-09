package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transfer_request")
@SuppressWarnings("unused")
public class TransferRequestEntity implements Serializable {

    public enum Status {
        PENDING,
        CANCELED,
        COMPLETED
    }

    private UUID id;
    private TicketEntity ticket;
    private UserEntity transferor;
    private UserEntity receiver;
    private Status status;
    private String receiverEmail;
    private OffsetDateTime createdDate;
    private OffsetDateTime completedDate;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public TransferRequestEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    public TicketEntity getTicket() {
        return ticket;
    }

    public TransferRequestEntity setTicket(TicketEntity ticket) {
        this.ticket = ticket;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferor_id", nullable = false)
    public UserEntity getTransferor() {
        return transferor;
    }

    public TransferRequestEntity setTransferor(UserEntity transferor) {
        this.transferor = transferor;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    public UserEntity getReceiver() {
        return receiver;
    }

    public TransferRequestEntity setReceiver(UserEntity receiver) {
        this.receiver = receiver;
        return this;
    }

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    public Status getStatus() {
        return status;
    }

    public TransferRequestEntity setStatus(Status status) {
        this.status = status;
        return this;
    }

    @Column(name = "receiver_email", nullable = false)
    public String getReceiverEmail() {
        return receiverEmail;
    }

    public TransferRequestEntity setReceiverEmail(String receiverEmail) {
        this.receiverEmail = receiverEmail;
        return this;
    }

    @Column(name = "created_d", nullable = false)
    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public TransferRequestEntity setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    @Column(name = "completed_d")
    public OffsetDateTime getCompletedDate() {
        return completedDate;
    }

    public TransferRequestEntity setCompletedDate(OffsetDateTime completedDate) {
        this.completedDate = completedDate;
        return this;
    }

    @Override
    public String toString() {
        return "TransferRequestEntity{" +
                "id=" + id +
                ", status=" + status +
                ", receiverEmail='" + receiverEmail + '\'' +
                ", createdDate=" + createdDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransferRequestEntity that = (TransferRequestEntity) o;
        return Objects.equals(id, that.id) &&
                status == that.status &&
                Objects.equals(receiverEmail, that.receiverEmail) &&
                Objects.equals(createdDate, that.createdDate) &&
                Objects.equals(completedDate, that.completedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, receiverEmail, createdDate, completedDate);
    }
}
