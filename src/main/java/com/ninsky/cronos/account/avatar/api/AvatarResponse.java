package com.ninsky.cronos.account.avatar.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "AvatarResponse")
public record AvatarResponse(
        @Schema(example = "https://cdn.cronos.example/avatars/3f9c1c9e-8f6a-4a8e-9a57-1f7a2b1c9d10/9b74c9897bac770f.jpg")
        String avatarUrl,
        LocalDateTime updatedAt
) {
}
