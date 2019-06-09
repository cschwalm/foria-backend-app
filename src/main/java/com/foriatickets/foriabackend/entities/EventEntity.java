package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "event")
@SuppressWarnings("unused")
public class EventEntity implements Serializable {

    private UUID id;
    private VenueEntity venueEntity;
    private String name;
    private DateTime eventTime;
    private String eventStreetAddress;
    private String eventCity;
    private String eventState;
    private String eventPostal;
    private String eventCountry;
    private int authorizedTickets;
    private BigDecimal ticketPrice;
    private Set<TicketEntity> tickets = new HashSet<>();

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public EventEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    public VenueEntity getVenueEntity() {
        return venueEntity;
    }

    public EventEntity setVenueEntity(VenueEntity venueEntity) {
        this.venueEntity = venueEntity;
        return this;
    }

    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public EventEntity setName(String name) {
        this.name = name;
        return this;
    }

    @Column(name = "event_time", nullable = false)
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    public DateTime getEventTime() {
        return eventTime;
    }

    public EventEntity setEventTime(DateTime eventTime) {
        this.eventTime = eventTime;
        return this;
    }

    @Column(name = "event_street_address", nullable = false)
    public String getEventStreetAddress() {
        return eventStreetAddress;
    }

    public EventEntity setEventStreetAddress(String eventStreetAddress) {
        this.eventStreetAddress = eventStreetAddress;
        return this;
    }

    @Column(name = "event_city", nullable = false)
    public String getEventCity() {
        return eventCity;
    }

    public EventEntity setEventCity(String eventCity) {
        this.eventCity = eventCity;
        return this;
    }

    @Column(name = "event_state", nullable = false)
    public String getEventState() {
        return eventState;
    }

    public EventEntity setEventState(String eventState) {
        this.eventState = eventState;
        return this;
    }

    @Column(name = "event_postal", length = 6)
    public String getEventPostal() {
        return eventPostal;
    }

    public EventEntity setEventPostal(String eventPostal) {
        this.eventPostal = eventPostal;
        return this;
    }

    @Column(name = "event_country", nullable = false, length = 3)
    public String getEventCountry() {
        return eventCountry;
    }

    public EventEntity setEventCountry(String eventCountry) {
        this.eventCountry = eventCountry;
        return this;
    }

    @Column(name = "authorized_tickets", nullable = false, length = 6)
    public int getAuthorizedTickets() {
        return authorizedTickets;
    }

    public EventEntity setAuthorizedTickets(int authorizedTickets) {
        this.authorizedTickets = authorizedTickets;
        return this;
    }

    @Column(name = "ticket_price", nullable = false, length = 6, precision = 2)
    public BigDecimal getTicketPrice() {
        return ticketPrice;
    }

    public EventEntity setTicketPrice(BigDecimal ticketPrice) {
        this.ticketPrice = ticketPrice;
        return this;
    }

    @OneToMany(mappedBy = "eventEntity", fetch = FetchType.LAZY)
    public Set<TicketEntity> getTickets() {
        return tickets;
    }

    public EventEntity setTickets(Set<TicketEntity> tickets) {
        this.tickets = tickets;
        return this;
    }

    @Override
    public String toString() {
        return "EventEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", eventTime=" + eventTime +
                ", eventStreetAddress='" + eventStreetAddress + '\'' +
                ", eventCity='" + eventCity + '\'' +
                ", eventState='" + eventState + '\'' +
                ", eventPostal='" + eventPostal + '\'' +
                ", eventCountry='" + eventCountry + '\'' +
                ", authorizedTickets=" + authorizedTickets +
                ", ticketPrice=" + ticketPrice +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventEntity that = (EventEntity) o;
        return authorizedTickets == that.authorizedTickets &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(eventTime, that.eventTime) &&
                Objects.equals(eventStreetAddress, that.eventStreetAddress) &&
                Objects.equals(eventCity, that.eventCity) &&
                Objects.equals(eventState, that.eventState) &&
                Objects.equals(eventPostal, that.eventPostal) &&
                Objects.equals(eventCountry, that.eventCountry) &&
                Objects.equals(ticketPrice, that.ticketPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, eventTime, eventStreetAddress, eventCity, eventState, eventPostal, eventCountry, authorizedTickets, ticketPrice);
    }
}
