package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserProfileJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserProfileMapper {

    public UserProfile toDomain(UserProfileJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserProfile.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .phoneNumber(entity.getPhoneNumber())
                .dateOfBirth(entity.getDateOfBirth())
                .gender(entity.getGender())
                .bio(entity.getBio())
                .profilePictureUrl(entity.getProfilePictureUrl())
                .coverPictureUrl(entity.getCoverPictureUrl())
                .address(entity.getAddress())
                .city(entity.getCity())
                .state(entity.getState())
                .postalCode(entity.getPostalCode())
                .country(entity.getCountry())
                .businessName(entity.getBusinessName())
                .businessType(entity.getBusinessType())
                .taxId(entity.getTaxId())
                .businessAddress(entity.getBusinessAddress())
                .businessCity(entity.getBusinessCity())
                .businessState(entity.getBusinessState())
                .businessPostalCode(entity.getBusinessPostalCode())
                .businessCountry(entity.getBusinessCountry())
                .businessPhone(entity.getBusinessPhone())
                .businessEmail(entity.getBusinessEmail())
                .businessWebsite(entity.getBusinessWebsite())
                .defaultTaxRate(entity.getDefaultTaxRate())
                .language(entity.getLanguage())
                .timezone(entity.getTimezone())
                .currency(entity.getCurrency())
                .linkedinUrl(entity.getLinkedinUrl())
                .twitterUrl(entity.getTwitterUrl())
                .facebookUrl(entity.getFacebookUrl())
                .instagramUrl(entity.getInstagramUrl())
                .emailNotifications(entity.isEmailNotifications())
                .smsNotifications(entity.isSmsNotifications())
                .pushNotifications(entity.isPushNotifications())
                .build();
    }

    public UserProfileJpaEntity toEntity(UserProfile domain) {
        if (domain == null) {
            return null;
        }
        return UserProfileJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .firstName(domain.getFirstName())
                .lastName(domain.getLastName())
                .phoneNumber(domain.getPhoneNumber())
                .dateOfBirth(domain.getDateOfBirth())
                .gender(domain.getGender())
                .bio(domain.getBio())
                .profilePictureUrl(domain.getProfilePictureUrl())
                .coverPictureUrl(domain.getCoverPictureUrl())
                .address(domain.getAddress())
                .city(domain.getCity())
                .state(domain.getState())
                .postalCode(domain.getPostalCode())
                .country(domain.getCountry())
                .businessName(domain.getBusinessName())
                .businessType(domain.getBusinessType())
                .taxId(domain.getTaxId())
                .businessAddress(domain.getBusinessAddress())
                .businessCity(domain.getBusinessCity())
                .businessState(domain.getBusinessState())
                .businessPostalCode(domain.getBusinessPostalCode())
                .businessCountry(domain.getBusinessCountry())
                .businessPhone(domain.getBusinessPhone())
                .businessEmail(domain.getBusinessEmail())
                .businessWebsite(domain.getBusinessWebsite())
                .defaultTaxRate(domain.getDefaultTaxRate())
                .language(domain.getLanguage())
                .timezone(domain.getTimezone())
                .currency(domain.getCurrency())
                .linkedinUrl(domain.getLinkedinUrl())
                .twitterUrl(domain.getTwitterUrl())
                .facebookUrl(domain.getFacebookUrl())
                .instagramUrl(domain.getInstagramUrl())
                .emailNotifications(domain.isEmailNotifications())
                .smsNotifications(domain.isSmsNotifications())
                .pushNotifications(domain.isPushNotifications())
                .build();
    }
}
