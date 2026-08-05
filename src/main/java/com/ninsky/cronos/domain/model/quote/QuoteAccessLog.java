package com.ninsky.cronos.domain.model.quote;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAccessLog {
    private UUID id;
    private UUID quoteId;
    private String ipAddress;
    private String userAgent;
    @Builder.Default
    private LocalDateTime accessedAt = LocalDateTime.now();
}
