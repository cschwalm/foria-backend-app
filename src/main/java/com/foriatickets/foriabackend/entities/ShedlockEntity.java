package com.foriatickets.foriabackend.entities;

import javax.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "shedlock")
public class ShedlockEntity implements Serializable {

    private String name;
    private OffsetDateTime lock_until;
    private OffsetDateTime locked_at;
    private String locked_by;

    @Id
    @GeneratedValue
    @Column(name = "name", length = 64)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Column(name = "lock_until", length = 3)
    public OffsetDateTime getLock_until() {
        return lock_until;
    }

    public void setLock_until(OffsetDateTime lock_until) {
        this.lock_until = lock_until;
    }

    @Column(name = "locked_at", length = 3)
    public OffsetDateTime getLocked_at() {
        return locked_at;
    }

    public void setLocked_at(OffsetDateTime locked_at) {
        this.locked_at = locked_at;
    }

    @Column(name = "locked_by", nullable = false)
    public String getLocked_by() {
        return locked_by;
    }

    public void setLocked_by(String locked_by) {
        this.locked_by = locked_by;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShedlockEntity that = (ShedlockEntity) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(lock_until, that.lock_until) &&
                Objects.equals(locked_at, that.locked_at) &&
                Objects.equals(locked_by, that.locked_by);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, lock_until, locked_at, locked_by);
    }
}
