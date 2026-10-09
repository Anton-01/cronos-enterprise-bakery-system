package com.ninsky.cronos.kitchen.section;

import java.util.UUID;

/**
 * A label of the user's section catalog (baking-studio §3.1). {@code usageCount} = lines of the user's live
 * recipes whose section matches the name under the section key.
 */
public record RecipeSection(UUID id, String name, String color, int displayOrder, int usageCount) {
}
