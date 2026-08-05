package com.ninsky.cronos.infrastructure.persistence.crypto;

import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Same transparent field-level encryption as {@link EncryptedStringConverter}, for LocalDate columns (e.g. date of birth). */
@Component
@Converter
public class EncryptedLocalDateConverter implements AttributeConverter<LocalDate, String> {

    private final FieldEncryptionService fieldEncryptionService;

    public EncryptedLocalDateConverter(FieldEncryptionService fieldEncryptionService) {
        this.fieldEncryptionService = fieldEncryptionService;
    }

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return fieldEncryptionService.encrypt(attribute == null ? null : attribute.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        String decrypted = fieldEncryptionService.decrypt(dbData);
        return decrypted == null ? null : LocalDate.parse(decrypted);
    }
}
