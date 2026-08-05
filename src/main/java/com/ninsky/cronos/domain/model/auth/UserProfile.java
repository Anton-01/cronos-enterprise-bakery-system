package com.ninsky.cronos.domain.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    private UUID id;
    private UUID userId;

    // Datos personales
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String bio;
    private String profilePictureUrl;
    private String coverPictureUrl;

    // Dirección personal
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String country;

    // Datos del negocio
    private String businessName;
    private String businessType;
    private String taxId;
    private String businessAddress;
    private String businessCity;
    private String businessState;
    private String businessPostalCode;
    private String businessCountry;
    private String businessPhone;
    private String businessEmail;
    private String businessWebsite;

    // Preferencias regionales y de sistema
    @Builder.Default
    private BigDecimal defaultTaxRate = new BigDecimal("16.00");
    @Builder.Default
    private String language = "es";
    private String timezone;
    @Builder.Default
    private String currency = "MXN";

    // Redes sociales
    private String linkedinUrl;
    private String twitterUrl;
    private String facebookUrl;
    private String instagramUrl;

    // Notificaciones
    @Builder.Default
    private boolean emailNotifications = true;
    @Builder.Default
    private boolean smsNotifications = false;
    @Builder.Default
    private boolean pushNotifications = true;

    public String getCompleteName() {
        return this.firstName + " " + this.lastName;
    }

    public String getBakerCompleteName() {
        return "Tu Repostero: " + this.firstName + " " + this.lastName;
    }

    public String getQuoteCompleteName() {
        return "Tu cotización de " + this.firstName + " " + this.lastName + " está lista";
    }
}
