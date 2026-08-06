package com.ninsky.cronos.application.event;

import lombok.Builder;
import java.util.UUID;

/**
 * Published after a recipe share link is created with a recipient email — see
 * {@code EmailNotificationListener}. Only published when there's actually someone to notify; the
 * "should we email at all" guard stays in {@code RecipeShareService}, not the listener.
 */
@Builder
public record RecipeSharedEvent(UUID shareId) {}
