package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@SuppressWarnings("unused")
@Table(name = "device_token")
public class DeviceTokenEntity implements Serializable {

    public enum TokenStatus {
        ACTIVE,
        DEACTIVATED
    }

    private UUID id;
    private UserEntity userEntity;
    private String deviceToken;
    private TokenStatus tokenStatus;
    private OffsetDateTime createdDate;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public DeviceTokenEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public UserEntity getUserEntity() {
        return userEntity;
    }

    public DeviceTokenEntity setUserEntity(UserEntity userEntity) {
        this.userEntity = userEntity;
        return this;
    }

    @Column(nullable = false, unique = true)
    public String getDeviceToken() {
        return deviceToken;
    }

    public DeviceTokenEntity setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
        return this;
    }

    @Column(name = "token_status", nullable = false)
    @Enumerated(EnumType.STRING)
    public TokenStatus getTokenStatus() {
        return tokenStatus;
    }

    public DeviceTokenEntity setTokenStatus(TokenStatus tokenStatus) {
        this.tokenStatus = tokenStatus;
        return this;
    }

    @Column(name = "created_d", nullable = false)
    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public DeviceTokenEntity setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    @Override
    public String toString() {
        return "DeviceTokenEntity{" +
                "id=" + id +
                ", deviceToken='" + deviceToken + '\'' +
                ", tokenStatus=" + tokenStatus +
                ", createdDate=" + createdDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceTokenEntity that = (DeviceTokenEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(deviceToken, that.deviceToken) &&
                tokenStatus == that.tokenStatus &&
                Objects.equals(createdDate, that.createdDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, deviceToken, tokenStatus, createdDate);
    }
}
