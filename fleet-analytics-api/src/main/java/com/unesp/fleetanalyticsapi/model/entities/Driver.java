package com.unesp.fleetanalyticsapi.model.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "drivers", schema = "fleet")
@Inheritance(strategy = InheritanceType.JOINED)
public class Driver extends User {

    @Column(name = "first_name", nullable = false, columnDefinition = "VARCHAR(100)")
    private String firstName;

    @Column(name = "last_name", nullable = false, columnDefinition = "VARCHAR(200)")
    private String lastName;

    private LocalDate birthDate;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE", nullable = false)
    private Boolean active;

    @ManyToMany
    @JoinTable(name = "driver_vehicle",
            schema = "fleet",
            joinColumns = @JoinColumn(name = "driver_id"),
            inverseJoinColumns = @JoinColumn(name = "vehicle_id"))
    private List<Vehicle> vehicles;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
