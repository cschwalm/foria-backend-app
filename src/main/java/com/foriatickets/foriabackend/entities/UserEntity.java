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
    private String firstName;
    private String lastName;
    private String email;
    private Set<TicketEntity> tickets = new HashSet<>();

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

    @Column(name = "email", unique = true, nullable = false)
    public String getEmail() {
        return email;
    }

    public UserEntity setEmail(String email) {
        this.email = email;
        return this;
    }

    @OneToMany(mappedBy = "ownerEntity", fetch = FetchType.LAZY)
    public Set<TicketEntity> getTickets() {
        return tickets;
    }

    public UserEntity setTickets(Set<TicketEntity> tickets) {
        this.tickets = tickets;
        return this;
    }

    @Override
    public String toString() {
        return "UserEntity{" +
                "id=" + id +
                ", auth0Id='" + auth0Id + '\'' +
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
                Objects.equals(firstName, that.firstName) &&
                Objects.equals(lastName, that.lastName) &&
                Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, auth0Id, firstName, lastName, email);
    }
}
