package com.bedrockcraftingcontrols;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config for Bedrock Crafting Controls.
 *
 * <p>The {@link ModConfigSpec} values are the source of truth on disk
 * ({@code config/bedrockcraftingcontrols-client.toml}). On (re)load they are copied into the
 * plain {@code volatile} fields below, which is what the Mixin reads on the click hot-path. Reading
 * the cached fields means the Mixin never has to touch the config system before it is loaded.
 */
public final class Config {

    private Config() {}

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_BEDROCK_RECIPE_CLICK_BEHAVIOR;
    public static final ModConfigSpec.BooleanValue NORMAL_CLICK_LOADS_RECIPE_ONLY;
    public static final ModConfigSpec.BooleanValue SHIFT_CLICK_RECIPE_CRAFTS_STACK;
    public static final ModConfigSpec.BooleanValue SHIFT_CLICK_OUTPUT_CRAFTS_MAX;
    public static final ModConfigSpec.BooleanValue CLICK_OUTPUT_REFILLS_GRID;
    public static final ModConfigSpec.BooleanValue AFFECT_INVENTORY_CRAFTING;

    // Cached copies read by the Mixin. Defaults mirror the spec defaults so behavior is correct
    // even in the (brief) window before the config file is read.
    public static volatile boolean enableBedrockRecipeClickBehavior = true;
    public static volatile boolean normalClickLoadsRecipeOnly = true;
    public static volatile boolean shiftClickRecipeCraftsStack = true;
    public static volatile boolean shiftClickOutputCraftsMax = true;
    public static volatile boolean clickOutputRefillsGrid = true;
    public static volatile boolean affectInventoryCrafting = false;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("crafting");

        ENABLE_BEDROCK_RECIPE_CLICK_BEHAVIOR = b.comment(
                        "Master switch. When false the mod does nothing and vanilla behavior is used.")
                .define("enableBedrockRecipeClickBehavior", true);

        NORMAL_CLICK_LOADS_RECIPE_ONLY = b.comment(
                        "Normal-click on a recipe book entry only loads the grid (no craft).",
                        "Vanilla Java already behaves this way; this flag documents the expectation",
                        "and is reserved for forward-compatibility. Disabling it does not change vanilla.")
                .define("normalClickLoadsRecipeOnly", true);

        SHIFT_CLICK_RECIPE_CRAFTS_STACK = b.comment(
                        "Shift-click on a recipe book entry crafts up to ONE STACK of the result into your",
                        "inventory (Bedrock-style), instead of vanilla's 'fill the grid with as many sets as",
                        "possible'. The craft count is floor(maxStackSize / yieldPerCraft), bounded by your",
                        "materials: e.g. a pickaxe -> 1, torches -> a stack of 64, stairs -> 64 (using more",
                        "than a stack of stone). This is the core Bedrock-style behavior added by this mod.")
                .define("shiftClickRecipeCraftsStack", true);

        SHIFT_CLICK_OUTPUT_CRAFTS_MAX = b.comment(
                        "Shift-click on the crafting OUTPUT slot crafts as many as the inventory allows",
                        "(Bedrock-style): the grid is re-filled from your inventory and everything is",
                        "crafted. Vanilla only crafts the sets already sitting in the grid. The mod hooks",
                        "this in MultiPlayerGameModeMixin; set false to restore vanilla output behavior.")
                .define("shiftClickOutputCraftsMax", true);

        CLICK_OUTPUT_REFILLS_GRID = b.comment(
                        "Normal-click (not shift) on the crafting OUTPUT re-stocks the grid from your",
                        "inventory after the craft, so the ingredients stay on the table and you can keep",
                        "clicking the output to pile crafts onto your cursor up to a full stack (Bedrock-",
                        "style). Vanilla empties the grid after one craft, ending the interaction. The mod",
                        "hooks this in MultiPlayerGameModeMixin; set false to restore vanilla behavior.")
                .define("clickOutputRefillsGrid", true);

        AFFECT_INVENTORY_CRAFTING = b.comment(
                        "Also apply the Bedrock crafting behaviors (craft-a-stack on a recipe, craft-max on",
                        "the output) to the 2x2 inventory crafting grid.",
                        "Disabled by default: only the 3x3 crafting table is affected.")
                .define("affectInventoryCrafting", false);

        b.pop();
        SPEC = b.build();
    }

    /** Copy the on-disk spec values into the cached fields read by the Mixin. */
    public static void bake() {
        enableBedrockRecipeClickBehavior = ENABLE_BEDROCK_RECIPE_CLICK_BEHAVIOR.get();
        normalClickLoadsRecipeOnly = NORMAL_CLICK_LOADS_RECIPE_ONLY.get();
        shiftClickRecipeCraftsStack = SHIFT_CLICK_RECIPE_CRAFTS_STACK.get();
        shiftClickOutputCraftsMax = SHIFT_CLICK_OUTPUT_CRAFTS_MAX.get();
        clickOutputRefillsGrid = CLICK_OUTPUT_REFILLS_GRID.get();
        affectInventoryCrafting = AFFECT_INVENTORY_CRAFTING.get();
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            bake();
        }
    }
}
