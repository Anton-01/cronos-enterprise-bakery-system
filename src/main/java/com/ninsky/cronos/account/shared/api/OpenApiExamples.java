package com.ninsky.cronos.account.shared.api;

/** Response examples for the account-settings OpenAPI docs, matching the frontend contract verbatim. */
public final class OpenApiExamples {

    private OpenApiExamples() {
    }

    public static final String USER_RESPONSE = """
            {"meta": {"traceId": "5f0c6a2e-1c1b-4c4e-9a4b-2b1f3c9d8e7a", "timestamp": "2026-09-26T18:00:00Z"},
             "status": "SUCCESS", "message": "Profile retrieved successfully",
             "data": {"id": "3f9c1c9e-8f6a-4a8e-9a57-1f7a2b1c9d10", "username": "admin_cronos", "email": "admin@cronos.com",
                      "firstName": "Antón", "lastName": "Admin", "phoneNumber": "+525512345678",
                      "avatarUrl": "https://cdn.cronos.example/avatars/3f9c1c9e-8f6a-4a8e-9a57-1f7a2b1c9d10/9b74c9897bac770f.jpg",
                      "enabled": true, "accountNonLocked": true, "twoFactorEnabled": false,
                      "failedLoginAttempts": 0, "lockedUntil": null,
                      "lastLoginAt": "2026-09-26T17:55:10", "passwordChangedAt": "2026-08-01T10:00:00",
                      "roles": ["SUPER_ADMIN"], "createdAt": "2026-01-10T09:00:00", "updatedAt": "2026-09-26T18:00:00"}}""";

    public static final String PHONE_VALIDATION_ERROR = """
            {"meta": {"traceId": "5f0c6a2e-1c1b-4c4e-9a4b-2b1f3c9d8e7a", "timestamp": "2026-09-26T18:00:00Z"},
             "status": "ERROR", "message": "Validation Failed",
             "errors": [{"code": "VALIDATION_FIELD_ERROR", "field": "phoneNumber",
                         "message": "Phone number must be a valid international number in E.164 format (e.g. +525512345678)"}]}""";

    public static final String DUPLICATE_USERNAME = """
            {"meta": {"traceId": "5f0c6a2e-1c1b-4c4e-9a4b-2b1f3c9d8e7a", "timestamp": "2026-09-26T18:00:00Z"},
             "status": "ERROR", "message": "Duplicate Resource",
             "errors": [{"code": "DUPLICATE_RESOURCE", "field": "username", "message": "Username 'admin_cronos' is already taken"}]}""";

    public static final String AVATAR_RESPONSE = """
            {"meta": {"traceId": "5f0c6a2e-1c1b-4c4e-9a4b-2b1f3c9d8e7a", "timestamp": "2026-09-26T18:00:00Z"},
             "status": "SUCCESS", "message": "Avatar updated successfully",
             "data": {"avatarUrl": "https://cdn.cronos.example/avatars/3f9c1c9e-8f6a-4a8e-9a57-1f7a2b1c9d10/9b74c9897bac770f.jpg",
                      "updatedAt": "2026-09-26T18:00:00"}}""";

    public static final String AVATAR_TOO_SMALL = """
            {"meta": {"traceId": "5f0c6a2e-1c1b-4c4e-9a4b-2b1f3c9d8e7a", "timestamp": "2026-09-26T18:00:00Z"},
             "status": "ERROR", "message": "Validation Failed",
             "errors": [{"code": "VALIDATION_FIELD_ERROR", "field": "file", "message": "Image must be at least 128 px on its shorter side"}]}""";

    public static final String NULL_DATA = """
            {"meta": {"traceId": "5f0c6a2e-1c1b-4c4e-9a4b-2b1f3c9d8e7a", "timestamp": "2026-09-26T18:00:00Z"},
             "status": "SUCCESS", "message": "No fiscal data registered", "data": null}""";

    public static final String FISCAL_RESPONSE = """
            {"meta": {"traceId": "5f0c6a2e-1c1b-4c4e-9a4b-2b1f3c9d8e7a", "timestamp": "2026-09-26T18:00:00Z"},
             "status": "SUCCESS", "message": "Fiscal data saved successfully",
             "data": {"legalName": "PASTELERIA CRONOS", "taxId": "GODE561231GR8", "taxRegime": "626", "taxpayerType": "INDIVIDUAL",
                      "address": {"street": "Av. Reforma", "exteriorNumber": "222", "interiorNumber": null, "neighborhood": "Juárez",
                                  "municipality": "Cuauhtémoc", "state": "CMX", "zipCode": "06600", "country": "MEX"},
                      "updatedAt": "2026-09-26T18:00:00"}}""";

    public static final String FISCAL_REQUEST = """
            {"legalName": "PASTELERIA CRONOS", "taxId": "GODE561231GR8", "taxRegime": "626",
             "address": {"street": "Av. Reforma", "exteriorNumber": "222", "interiorNumber": null, "neighborhood": "Juárez",
                         "municipality": "Cuauhtémoc", "state": "CMX", "zipCode": "06600", "country": "MEX"}}""";

    public static final String FISCAL_VALIDATION_ERROR = """
            {"meta": {"traceId": "5f0c6a2e-1c1b-4c4e-9a4b-2b1f3c9d8e7a", "timestamp": "2026-09-26T18:00:00Z"},
             "status": "ERROR", "message": "Validation Failed",
             "errors": [{"code": "VALIDATION_FIELD_ERROR", "field": "address.zipCode", "message": "Zip code must be 5 digits (e.g. 06600)"},
                        {"code": "VALIDATION_FIELD_ERROR", "field": "taxRegime", "message": "Tax regime 601 does not apply to INDIVIDUAL taxpayers"}]}""";
}
