package com.unesp.fleetanalyticsapi.model.entities;

import com.unesp.fleetanalyticsapi.model.enums.StateCode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "addresses", schema = "fleet")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "address_line")
    private String addressLine;

    @Column(length = 30)
    private String number;

    private String neighborhood;

    private String additionalAddressDetails;

    private String cep;

    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_code", columnDefinition = "VARCHAR(2)", nullable = false)
    private StateCode stateCode;

    private String country;

    @CreatedDate
    private ZonedDateTime createdAt;

    @LastModifiedDate
    private ZonedDateTime updatedAt;

    @OneToOne(mappedBy = "address")
    private Company company;

}
