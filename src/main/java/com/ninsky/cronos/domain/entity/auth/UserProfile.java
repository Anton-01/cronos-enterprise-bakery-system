package com.ninsky.cronos.domain.entity.auth;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_profiles")
public class UserProfile extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Relación 1 a 1 con la entidad principal de autenticación
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // ========================================================================
    // DATOS PERSONALES
    // ========================================================================
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;

    @Column(length = 500)
    private String bio;

    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    @Column(name = "cover_picture_url", length = 500)
    private String coverPictureUrl;

    // ========================================================================
    // DIRECCIÓN PERSONAL
    // ========================================================================
    @Column(length = 500) private String address;
    @Column(length = 100) private String city;
    @Column(length = 100) private String state;
    @Column(name = "postal_code", length = 20) private String postalCode;
    @Column(length = 100) private String country;

    // ========================================================================
    // DATOS DEL NEGOCIO (Repostería/Empresa)
    // ========================================================================
    @Column(name = "business_name", length = 255) private String businessName;
    @Column(name = "business_type", length = 100) private String businessType;
    @Column(name = "tax_id", length = 50) private String taxId;
    @Column(name = "business_address", length = 500) private String businessAddress;
    @Column(name = "business_city", length = 100) private String businessCity;
    @Column(name = "business_state", length = 100) private String businessState;
    @Column(name = "business_postal_code", length = 20) private String businessPostalCode;
    @Column(name = "business_country", length = 100) private String businessCountry;
    @Column(name = "business_phone", length = 20) private String businessPhone;
    @Column(name = "business_email", length = 255) private String businessEmail;
    @Column(name = "business_website", length = 255) private String businessWebsite;

    // ========================================================================
    // PREFERENCIAS REGIONALES Y DE SISTEMA
    // ========================================================================
    @Column(name = "default_tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal defaultTaxRate = new BigDecimal("16.00");

    @Column(length = 10)
    @Builder.Default
    private String language = "es";

    @Column(length = 50)
    private String timezone;

    @Column(length = 10)
    @Builder.Default
    private String currency = "MXN";

    // ========================================================================
    // REDES SOCIALES
    // ========================================================================
    @Column(name = "linkedin_url", length = 255) private String linkedinUrl;
    @Column(name = "twitter_url", length = 255) private String twitterUrl;
    @Column(name = "facebook_url", length = 255) private String facebookUrl;
    @Column(name = "instagram_url", length = 255) private String instagramUrl;

    // ========================================================================
    // CONFIGURACIÓN DE NOTIFICACIONES
    // ========================================================================
    @Column(name = "email_notifications", nullable = false)
    @Builder.Default
    private boolean emailNotifications = true;

    @Column(name = "sms_notifications", nullable = false)
    @Builder.Default
    private boolean smsNotifications = false;

    @Column(name = "push_notifications", nullable = false)
    @Builder.Default
    private boolean pushNotifications = true;

    public String getCompleteName(){
        return this.firstName + " " + this.lastName;
    }

    public String getBakerCompleteName(){
        return "Tu Repostero: " + this.firstName + " " + this.lastName;
    }

    public String getQuoteCompleteName(){
        return "Tu cotización de " + this.firstName + " " + this.lastName + " está lista";
    }
}
