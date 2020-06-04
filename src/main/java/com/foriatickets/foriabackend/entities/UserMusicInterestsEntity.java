package com.foriatickets.foriabackend.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_music_interests")
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class UserMusicInterestsEntity implements Serializable {

    private UUID id;
    private UserEntity userEntity;
    private LocalDate processedDate;
    private String data;

    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    @Column(name = "id", updatable = false)
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public UserEntity getUserEntity() {
        return userEntity;
    }

    public void setUserEntity(UserEntity userEntity) {
        this.userEntity = userEntity;
    }

    @Column(name = "processed_d", nullable = false)
    public LocalDate getProcessedDate() {
        return processedDate;
    }

    public void setProcessedDate(LocalDate date) {
        this.processedDate = date;
    }

    @Lob
    @Column(name = "data", nullable = false)
    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserMusicInterestsEntity that = (UserMusicInterestsEntity) o;
        return id.equals(that.id) &&
                processedDate.equals(that.processedDate) &&
                data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, processedDate, data);
    }

    @Override
    public String toString() {
        return "UserMusicInterestsEntity{" +
                "id='" + id + '\'' +
                ", processedDate=" + processedDate +
                ", data='" + data + '\'' +
                '}';
    }
}
