package com.bedrockcraftingcontrols;

/**
 * Pure decision logic for the mod's behavior changes, extracted from the Mixin so it can be
 * unit-tested without a running Minecraft client.
 *
 * <p>The Mixin ({@code com.bedrockcraftingcontrols.mixin.MultiPlayerGameModeMixin}) is responsible
 * for the parts that genuinely need the game (fetching the player, matching the container id, the
 * vanilla packet calls). The yes/no questions — "given the config and what the player did, should we
 * intercept?" — are just boolean algebra, so they live here where a plain JUnit test can cover the
 * whole truth table.
 */
public final class RecipeClickPolicy {

    private RecipeClickPolicy() {}

    /**
     * Decide whether a recipe-book click should be intercepted and turned into a craft-up-to-a-stack.
     *
     * @param enableBedrockRecipeClickBehavior master switch ({@link Config#enableBedrockRecipeClickBehavior})
     * @param shiftClickRecipeCraftsStack the core feature flag ({@link Config#shiftClickRecipeCraftsStack})
     * @param shiftDown whether Shift was held (vanilla passes this as {@code placeAll})
     * @param isCraftingTable whether the open menu is a 3x3 {@code CraftingMenu}
     * @param isInventoryGrid whether the open menu is the 2x2 {@code InventoryMenu}
     * @param affectInventoryCrafting whether the 2x2 grid is opted in ({@link Config#affectInventoryCrafting})
     * @return {@code true} only when the mod should craft the stack; {@code false} means "leave it to vanilla"
     */
    public static boolean shouldCraftStack(
            boolean enableBedrockRecipeClickBehavior,
            boolean shiftClickRecipeCraftsStack,
            boolean shiftDown,
            boolean isCraftingTable,
            boolean isInventoryGrid,
            boolean affectInventoryCrafting) {
        // Master switch + the only flag that actually changes behavior.
        if (!enableBedrockRecipeClickBehavior || !shiftClickRecipeCraftsStack) {
            return false;
        }
        // A normal (non-shift) click already just loads the grid in vanilla; never intercept it.
        if (!shiftDown) {
            return false;
        }
        return isAffectedMenu(isCraftingTable, isInventoryGrid, affectInventoryCrafting);
    }

    /**
     * How many times to craft so a single shift-click yields up to one stack of the result, matching
     * Bedrock. The total produced is {@code count * resultCountPerCraft}, which is the largest multiple
     * of the per-craft yield that still fits in one inventory slot ({@code <= resultMaxStackSize}).
     *
     * <p>Examples: a stone pickaxe (yields 1, stacks to 1) → 1 craft; a torch (yields 4, stacks to 64)
     * → 16 crafts (64 torches); stairs (yield 4, stack 64) → 16 crafts (64 stairs); a slab (yields 6,
     * stack 64) → 10 crafts (60 slabs). The actual count is still bounded at runtime by the materials
     * in the inventory — extra craft attempts simply no-op.
     *
     * @param resultCountPerCraft items produced by one craft (the recipe's output count)
     * @param resultMaxStackSize the result item's max stack size (the most that fit in one slot)
     * @return the number of crafts to perform; always at least 1, even for odd/empty inputs
     */
    public static int stackCraftCount(int resultCountPerCraft, int resultMaxStackSize) {
        if (resultCountPerCraft <= 0 || resultMaxStackSize <= 0) {
            return 1; // special/unknown result -> fall back to a single craft
        }
        return Math.max(1, resultMaxStackSize / resultCountPerCraft);
    }

    /**
     * Decide whether a click on the crafting <em>output</em> slot should be intercepted and turned
     * into a Bedrock-style "craft as many as the inventory allows" (re-fill the grid from inventory,
     * then craft everything in it). Vanilla only crafts whatever sets already sit in the grid.
     *
     * @param enableBedrockRecipeClickBehavior master switch ({@link Config#enableBedrockRecipeClickBehavior})
     * @param shiftClickOutputCraftsMax the output-max feature flag ({@link Config#shiftClickOutputCraftsMax})
     * @param isQuickMove whether this was a shift-click ({@code ClickType.QUICK_MOVE})
     * @param isResultSlot whether the clicked slot is the crafting result slot (index 0)
     * @param isCraftingTable whether the open menu is a 3x3 {@code CraftingMenu}
     * @param isInventoryGrid whether the open menu is the 2x2 {@code InventoryMenu}
     * @param affectInventoryCrafting whether the 2x2 grid is opted in ({@link Config#affectInventoryCrafting})
     * @return {@code true} only when the mod should craft the maximum; {@code false} means "leave it to vanilla"
     */
    public static boolean shouldCraftMaxOnOutput(
            boolean enableBedrockRecipeClickBehavior,
            boolean shiftClickOutputCraftsMax,
            boolean isQuickMove,
            boolean isResultSlot,
            boolean isCraftingTable,
            boolean isInventoryGrid,
            boolean affectInventoryCrafting) {
        if (!enableBedrockRecipeClickBehavior || !shiftClickOutputCraftsMax) {
            return false;
        }
        // Only a shift-click (quick-move) on the result slot is the "craft" gesture.
        if (!isQuickMove || !isResultSlot) {
            return false;
        }
        return isAffectedMenu(isCraftingTable, isInventoryGrid, affectInventoryCrafting);
    }

    /**
     * Decide whether a normal (non-shift) click on the crafting <em>output</em> slot should keep the
     * grid stocked — i.e. re-place one ingredient set from the inventory after the craft so the player
     * can keep clicking the output to pile crafts onto the cursor (Bedrock-style). Vanilla empties the
     * grid after one craft, ending the interaction.
     *
     * @param enableBedrockRecipeClickBehavior master switch ({@link Config#enableBedrockRecipeClickBehavior})
     * @param clickOutputRefillsGrid the feature flag ({@link Config#clickOutputRefillsGrid})
     * @param isPickup whether this was a normal pickup click ({@code ClickType.PICKUP})
     * @param isResultSlot whether the clicked slot is the crafting result slot (index 0)
     * @param isCraftingTable whether the open menu is a 3x3 {@code CraftingMenu}
     * @param isInventoryGrid whether the open menu is the 2x2 {@code InventoryMenu}
     * @param affectInventoryCrafting whether the 2x2 grid is opted in ({@link Config#affectInventoryCrafting})
     * @return {@code true} only when the mod should re-stock the grid; {@code false} means "leave it to vanilla"
     */
    public static boolean shouldRefillGridOnOutputClick(
            boolean enableBedrockRecipeClickBehavior,
            boolean clickOutputRefillsGrid,
            boolean isPickup,
            boolean isResultSlot,
            boolean isCraftingTable,
            boolean isInventoryGrid,
            boolean affectInventoryCrafting) {
        if (!enableBedrockRecipeClickBehavior || !clickOutputRefillsGrid) {
            return false;
        }
        // Only a normal pickup click on the result slot is the "craft to cursor" gesture.
        if (!isPickup || !isResultSlot) {
            return false;
        }
        return isAffectedMenu(isCraftingTable, isInventoryGrid, affectInventoryCrafting);
    }

    /**
     * The shared menu-scope rule: the 3x3 table is always affected; the 2x2 inventory grid only when
     * opted in. Furnaces, smithing tables, stonecutters, anvils, etc. report neither and are skipped.
     */
    private static boolean isAffectedMenu(
            boolean isCraftingTable, boolean isInventoryGrid, boolean affectInventoryCrafting) {
        return isCraftingTable || (isInventoryGrid && affectInventoryCrafting);
    }
}
