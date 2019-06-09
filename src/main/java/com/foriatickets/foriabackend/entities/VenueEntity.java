package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "venue")
@SuppressWarnings("unused")
public class VenueEntity implements Serializable {

    private UUID id;
    private String name;
    private String description;
    private String contactName;
    private String contactEmail;
    private String contactPhoneCountry;
    private String contactPhone;
    private String contactStreetAddress;
    private String contactCity;
    private String contactState;
    private String contactZip;
    private String contactCountry;
    private Set<EventEntity> events = new HashSet<>();

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public VenueEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public VenueEntity setName(String name) {
        this.name = name;
        return this;
    }

    @Column(name = "description", nullable = false)
    public String getDescription() {
        return description;
    }

    public VenueEntity setDescription(String description) {
        this.description = description;
        return this;
    }

    @Column(name = "contact_name", nullable = false)
    public String getContactName() {
        return contactName;
    }

    public VenueEntity setContactName(String contactName) {
        this.contactName = contactName;
        return this;
    }

    @Column(name = "contact_email", nullable = false)
    public String getContactEmail() {
        return contactEmail;
    }

    public VenueEntity setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
        return this;
    }

    @Column(name = "contact_phone_country", length = 5, nullable = false)
    public String getContactPhoneCountry() {
        return contactPhoneCountry;
    }

    public VenueEntity setContactPhoneCountry(String contactPhoneCountry) {
        this.contactPhoneCountry = contactPhoneCountry;
        return this;
    }

    @Column(name = "contact_phone", nullable = false)
    public String getContactPhone() {
        return contactPhone;
    }

    public VenueEntity setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
        return this;
    }

    @Column(name = "contact_street_address", nullable = false)
    public String getContactStreetAddress() {
        return contactStreetAddress;
    }

    public VenueEntity setContactStreetAddress(String contactStreetAddress) {
        this.contactStreetAddress = contactStreetAddress;
        return this;
    }

    @Column(name = "contact_city", nullable = false)
    public String getContactCity() {
        return contactCity;
    }

    public VenueEntity setContactCity(String contactCity) {
        this.contactCity = contactCity;
        return this;
    }

    @Column(name = "contact_state", nullable = false)
    public String getContactState() {
        return contactState;
    }

    public VenueEntity setContactState(String contactState) {
        this.contactState = contactState;
        return this;
    }

    @Column(name = "contact_postal", length = 6)
    public String getContactZip() {
        return contactZip;
    }

    public VenueEntity setContactZip(String contactZip) {
        this.contactZip = contactZip;
        return this;
    }

    @Column(name = "contact_country", length = 3, nullable = false)
    public String getContactCountry() {
        return contactCountry;
    }

    public VenueEntity setContactCountry(String contactCountry) {
        this.contactCountry = contactCountry;
        return this;
    }

    @OneToMany(mappedBy = "venueEntity", fetch = FetchType.LAZY)
    public Set<EventEntity> getEvents() {
        return events;
    }

    public VenueEntity setEvents(Set<EventEntity> events) {
        this.events = events;
        return this;
    }

    @Override
    public String toString() {
        return "VenueEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", contactName='" + contactName + '\'' +
                ", contactEmail='" + contactEmail + '\'' +
                ", contactPhoneCountry='" + contactPhoneCountry + '\'' +
                ", contactPhone='" + contactPhone + '\'' +
                ", contactStreetAddress='" + contactStreetAddress + '\'' +
                ", contactCity='" + contactCity + '\'' +
                ", contactState='" + contactState + '\'' +
                ", contactZip='" + contactZip + '\'' +
                ", contactCountry='" + contactCountry + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VenueEntity that = (VenueEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(contactName, that.contactName) &&
                Objects.equals(contactEmail, that.contactEmail) &&
                Objects.equals(contactPhoneCountry, that.contactPhoneCountry) &&
                Objects.equals(contactPhone, that.contactPhone) &&
                Objects.equals(contactStreetAddress, that.contactStreetAddress) &&
                Objects.equals(contactState, that.contactState) &&
                Objects.equals(contactCity, that.contactCity) &&
                Objects.equals(contactZip, that.contactZip) &&
                Objects.equals(contactCountry, that.contactCountry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, contactName, contactEmail, contactPhoneCountry, contactPhone, contactStreetAddress, contactCity, contactState, contactZip, contactCountry);
    }
}
