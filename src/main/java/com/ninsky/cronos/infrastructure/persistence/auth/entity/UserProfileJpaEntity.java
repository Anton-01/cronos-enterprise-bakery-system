package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import com.ninsky.cronos.infrastructure.persistence.crypto.EncryptedLocalDateConverter;
import com.ninsky.cronos.infrastructure.persistence.crypto.EncryptedStringConverter;
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
public class UserProfileJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number", columnDefinition = "TEXT")
    private String phoneNumber;

    @Convert(converter = EncryptedLocalDateConverter.class)
    @Column(name = "date_of_birth", columnDefinition = "TEXT")
    private LocalDate dateOfBirth;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String gender;

    @Column(length = 500)
    private String bio;

    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    @Column(name = "cover_picture_url", length = 500)
    private String coverPictureUrl;

    @Convert(converter = EncryptedStringConverter.class) @Column(columnDefinition = "TEXT") private String address;
    @Convert(converter = EncryptedStringConverter.class) @Column(columnDefinition = "TEXT") private String city;
    @Convert(converter = EncryptedStringConverter.class) @Column(columnDefinition = "TEXT") private String state;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "postal_code", columnDefinition = "TEXT") private String postalCode;
    @Convert(converter = EncryptedStringConverter.class) @Column(columnDefinition = "TEXT") private String country;

    @Convert(converter = EncryptedStringConverter.class) @Column(name = "business_name", columnDefinition = "TEXT") private String businessName;
    @Column(name = "business_type", length = 100) private String businessType;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "tax_id", columnDefinition = "TEXT") private String taxId;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "business_address", columnDefinition = "TEXT") private String businessAddress;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "business_city", columnDefinition = "TEXT") private String businessCity;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "business_state", columnDefinition = "TEXT") private String businessState;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "business_postal_code", columnDefinition = "TEXT") private String businessPostalCode;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "business_country", columnDefinition = "TEXT") private String businessCountry;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "business_phone", columnDefinition = "TEXT") private String businessPhone;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "business_email", columnDefinition = "TEXT") private String businessEmail;
    @Column(name = "business_website", length = 255) private String businessWebsite;

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

    @Column(name = "linkedin_url", length = 255) private String linkedinUrl;
    @Column(name = "twitter_url", length = 255) private String twitterUrl;
    @Column(name = "facebook_url", length = 255) private String facebookUrl;
    @Column(name = "instagram_url", length = 255) private String instagramUrl;

    @Column(name = "email_notifications", nullable = false)
    @Builder.Default
    private boolean emailNotifications = true;

    @Column(name = "sms_notifications", nullable = false)
    @Builder.Default
    private boolean smsNotifications = false;

    @Column(name = "push_notifications", nullable = false)
    @Builder.Default
    private boolean pushNotifications = true;
}
