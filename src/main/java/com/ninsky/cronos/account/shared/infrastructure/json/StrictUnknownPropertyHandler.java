package com.ninsky.cronos.account.shared.infrastructure.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.ninsky.cronos.account.shared.api.RejectUnknownProperties;

import java.io.IOException;

/**
 * Makes unknown JSON properties a hard 400 for DTOs annotated {@link RejectUnknownProperties}
 * only — the app-wide {@code FAIL_ON_UNKNOWN_PROPERTIES} stays at Spring Boot's lenient default so
 * no existing endpoint changes behaviour. Mass-assignment guard: {@code email}, {@code roles},
 * {@code taxpayerType}, ... sent to an account endpoint are rejected, not silently dropped.
 */
public class StrictUnknownPropertyHandler extends DeserializationProblemHandler {

    @Override
    public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p, JsonDeserializer<?> deserializer,
                                         Object beanOrClass, String propertyName) throws IOException {
        Class<?> type = beanOrClass instanceof Class<?> c ? c : beanOrClass.getClass();
        if (type.isAnnotationPresent(RejectUnknownProperties.class)) {
            throw UnrecognizedPropertyException.from(p, beanOrClass, propertyName, deserializer.getKnownPropertyNames());
        }
        return false;
    }
}
