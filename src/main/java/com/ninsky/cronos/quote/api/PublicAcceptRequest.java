package com.ninsky.cronos.quote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The client accepts from the public link (§4.6). */
public record PublicAcceptRequest(@NotBlank @Size(min = 2, max = 150) String clientName, @Size(max = 500) String comment) {
}
