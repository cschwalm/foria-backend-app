package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user")
@SuppressWarnings("unused")
public class UserEntity implements Serializable {

    private UUID id;
    private String auth0Id;
    private String stripeId;
    private String firstName;
    private String lastName;
    private String email;
    private Set<TicketEntity> tickets = new HashSet<>();
    private Set<TicketEntity> purchasedTickets = new HashSet<>();

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public UserEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @Column(name = "auth0_id", unique = true, nullable = false)
    public String getAuth0Id() {
        return auth0Id;
    }

    public UserEntity setAuth0Id(String auth0Id) {
        this.auth0Id = auth0Id;
        return this;
    }

    @Column(name = "stripe_id", unique = true)
    public String getStripeId() {
        return stripeId;
    }

    public UserEntity setStripeId(String stripeId) {
        this.stripeId = stripeId;
        return this;
    }

    @Column(name = "first_name", nullable = false)
    public String getFirstName() {
        return firstName;
    }

    public UserEntity setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    @Column(name = "last_name", nullable = false)
    public String getLastName() {
        return lastName;
    }

    public UserEntity setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    @Column(name = "email", unique = true)
    public String getEmail() {
        return email;
    }

    public UserEntity setEmail(String email) {
        this.email = email;
        return this;
    }

    @OneToMany(mappedBy = "ownerEntity", fetch = FetchType.EAGER)
    public Set<TicketEntity> getTickets() {
        return tickets;
    }

    public UserEntity setTickets(Set<TicketEntity> tickets) {
        this.tickets = tickets;
        return this;
    }

    @OneToMany(mappedBy = "purchaserEntity", fetch = FetchType.LAZY)
    public Set<TicketEntity> getPurchasedTickets() {
        return purchasedTickets;
    }

    public UserEntity setPurchasedTickets(Set<TicketEntity> purchasedTickets) {
        this.purchasedTickets = purchasedTickets;
        return this;
    }

    @Override
    public String toString() {
        return "UserEntity{" +
                "key=" + id +
                ", auth0Id='" + auth0Id + '\'' +
                ", stripeId='" + auth0Id + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserEntity that = (UserEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(auth0Id, that.auth0Id) &&
                Objects.equals(stripeId, that.stripeId) &&
                Objects.equals(firstName, that.firstName) &&
                Objects.equals(lastName, that.lastName) &&
                Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, auth0Id, stripeId, firstName, lastName, email);
    }
}
