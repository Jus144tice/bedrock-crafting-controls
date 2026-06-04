package com.bedrockcraftingcontrols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the config schema. These don't load a config file from disk; they assert the spec's
 * declared structure and defaults, plus that the cached fields the Mixin reads start out matching
 * those defaults (so behavior is correct even before the file is first read). The NeoForge runtime
 * is on the test classpath via the moddev {@code unitTest} harness, which is what lets us touch
 * {@code ModConfigSpec} here.
 */
class ConfigTest {

    @Test
    @DisplayName("the spec builds")
    void specBuilds() {
        assertNotNull(Config.SPEC, "Config.SPEC should be built by the static initializer");
    }

    @Test
    @DisplayName("every config value is declared under the 'crafting' section with the expected key")
    void valuePaths() {
        assertEquals(
                List.of("crafting", "enableBedrockRecipeClickBehavior"),
                Config.ENABLE_BEDROCK_RECIPE_CLICK_BEHAVIOR.getPath());
        assertEquals(
                List.of("crafting", "normalClickLoadsRecipeOnly"), Config.NORMAL_CLICK_LOADS_RECIPE_ONLY.getPath());
        assertEquals(
                List.of("crafting", "shiftClickRecipeCraftsStack"), Config.SHIFT_CLICK_RECIPE_CRAFTS_STACK.getPath());
        assertEquals(List.of("crafting", "shiftClickOutputCraftsMax"), Config.SHIFT_CLICK_OUTPUT_CRAFTS_MAX.getPath());
        assertEquals(List.of("crafting", "clickOutputRefillsGrid"), Config.CLICK_OUTPUT_REFILLS_GRID.getPath());
        assertEquals(List.of("crafting", "affectInventoryCrafting"), Config.AFFECT_INVENTORY_CRAFTING.getPath());
    }

    @Test
    @DisplayName("spec defaults match the documented behavior (only affectInventoryCrafting is off)")
    void specDefaults() {
        assertTrue(Config.ENABLE_BEDROCK_RECIPE_CLICK_BEHAVIOR.getDefault());
        assertTrue(Config.NORMAL_CLICK_LOADS_RECIPE_ONLY.getDefault());
        assertTrue(Config.SHIFT_CLICK_RECIPE_CRAFTS_STACK.getDefault());
        assertTrue(Config.SHIFT_CLICK_OUTPUT_CRAFTS_MAX.getDefault());
        assertTrue(Config.CLICK_OUTPUT_REFILLS_GRID.getDefault());
        assertFalse(Config.AFFECT_INVENTORY_CRAFTING.getDefault());
    }

    @Test
    @DisplayName("cached fields start at the spec defaults, so the Mixin reads correct values pre-load")
    void cachedDefaultsMirrorSpec() {
        assertEquals(Config.ENABLE_BEDROCK_RECIPE_CLICK_BEHAVIOR.getDefault(), Config.enableBedrockRecipeClickBehavior);
        assertEquals(Config.NORMAL_CLICK_LOADS_RECIPE_ONLY.getDefault(), Config.normalClickLoadsRecipeOnly);
        assertEquals(Config.SHIFT_CLICK_RECIPE_CRAFTS_STACK.getDefault(), Config.shiftClickRecipeCraftsStack);
        assertEquals(Config.SHIFT_CLICK_OUTPUT_CRAFTS_MAX.getDefault(), Config.shiftClickOutputCraftsMax);
        assertEquals(Config.CLICK_OUTPUT_REFILLS_GRID.getDefault(), Config.clickOutputRefillsGrid);
        assertEquals(Config.AFFECT_INVENTORY_CRAFTING.getDefault(), Config.affectInventoryCrafting);
    }
}
