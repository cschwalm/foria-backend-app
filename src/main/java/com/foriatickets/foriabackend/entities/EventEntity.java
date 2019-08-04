package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "event")
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class EventEntity implements Serializable {

    private UUID id;
    private VenueEntity venueEntity;
    private String name;
    private String tagLine;
    private String imageUrl;
    private String description;
    private OffsetDateTime eventStartTime;
    private OffsetDateTime eventEndTime;
    private int authorizedTickets;
    private Set<TicketEntity> tickets = new HashSet<>();
    private Set<TicketFeeConfigEntity> ticketFeeConfig = new HashSet<>();
    private Set<TicketTypeConfigEntity> ticketTypeConfig = new HashSet<>();

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

    @Column(name = "tag_line", nullable = false)
    public String getTagLine() {
        return tagLine;
    }

    public EventEntity setTagLine(String tagLine) {
        this.tagLine = tagLine;
        return this;
    }

    @Column(name = "image_url", nullable = false)
    public String getImageUrl() {
        return imageUrl;
    }

    public EventEntity setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }

    @Column(name = "description", columnDefinition = "text", nullable = false)
    public String getDescription() {
        return description;
    }

    public EventEntity setDescription(String description) {
        this.description = description;
        return this;
    }

    @Column(name = "event_start_time", nullable = false)
    public OffsetDateTime getEventStartTime() {
        return eventStartTime;
    }

    public EventEntity setEventStartTime(OffsetDateTime eventStartTime) {
        this.eventStartTime = eventStartTime;
        return this;
    }

    @Column(name = "event_end_time", nullable = false)
    public OffsetDateTime getEventEndTime() {
        return eventEndTime;
    }

    public EventEntity setEventEndTime(OffsetDateTime eventEndTime) {
        this.eventEndTime = eventEndTime;
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

    @OneToMany(mappedBy = "eventEntity", fetch = FetchType.LAZY)
    public Set<TicketEntity> getTickets() {
        return tickets;
    }

    public EventEntity setTickets(Set<TicketEntity> tickets) {
        this.tickets = tickets;
        return this;
    }

    @OneToMany(mappedBy = "eventEntity", fetch = FetchType.EAGER)
    public Set<TicketFeeConfigEntity> getTicketFeeConfig() {
        return ticketFeeConfig;
    }

    public EventEntity setTicketFeeConfig(Set<TicketFeeConfigEntity> ticketFeeConfig) {
        this.ticketFeeConfig = ticketFeeConfig;
        return this;
    }

    @OneToMany(mappedBy = "eventEntity", fetch = FetchType.EAGER)
    public Set<TicketTypeConfigEntity> getTicketTypeConfigEntity() {
        return ticketTypeConfig;
    }

    public EventEntity setTicketTypeConfigEntity(Set<TicketTypeConfigEntity> ticketTypeConfig) {
        this.ticketTypeConfig = ticketTypeConfig;
        return this;
    }

    @Override
    public String toString() {
        return "EventEntity{" +
                "key=" + id +
                ", name='" + name + '\'' +
                ", tagLine='" + tagLine + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", description='" + description + '\'' +
                ", eventStartTime=" + eventStartTime +
                ", eventEndTime=" + eventEndTime +
                ", authorizedTickets=" + authorizedTickets +
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
                Objects.equals(tagLine, that.tagLine) &&
                Objects.equals(imageUrl, that.imageUrl) &&
                Objects.equals(description, that.description) &&
                Objects.equals(eventStartTime, that.eventStartTime) &&
                Objects.equals(eventEndTime, that.eventEndTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, tagLine, imageUrl, description, eventStartTime, eventEndTime, authorizedTickets);
    }
}
