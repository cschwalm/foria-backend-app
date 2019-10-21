package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@SuppressWarnings("unused")
@Table(name = "venue_access")
public class VenueAccessEntity implements Serializable {

    private UUID id;
    private VenueEntity venueEntity;
    private UserEntity userEntity;
    private OffsetDateTime createdDate;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public VenueAccessEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public UserEntity getUserEntity() {
        return userEntity;
    }

    public VenueAccessEntity setUserEntity(UserEntity userEntity) {
        this.userEntity = userEntity;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    public VenueEntity getVenueEntity() {
        return venueEntity;
    }

    public void setVenueEntity(VenueEntity venueEntity) {
        this.venueEntity = venueEntity;
    }

    @Column(name = "created_d", nullable = false)
    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public String toString() {
        return "VenueAccessEntity{" +
                "id=" + id +
                ", createdDate=" + createdDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VenueAccessEntity that = (VenueAccessEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(createdDate, that.createdDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdDate);
    }
}
