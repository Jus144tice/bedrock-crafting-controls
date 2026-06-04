package com.bedrockcraftingcontrols.mixin;

import com.bedrockcraftingcontrols.BedrockCraftingControls;
import com.bedrockcraftingcontrols.Config;
import com.bedrockcraftingcontrols.RecipeClickPolicy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Heart of the mod. Three recipe-book / crafting behaviors are intercepted here; all call only stock
 * vanilla client methods (so they work on vanilla servers — the server stays authoritative and
 * reconciles container state), and all delegate their yes/no decision to the pure, unit-tested
 * {@link RecipeClickPolicy}.
 *
 * <p><strong>1. Shift-click a recipe → craft up to a stack of the result</strong> (replaces vanilla
 * bulk-fill). In {@code handlePlaceRecipe} we repeat, {@code stackCraftCount} times: (a)
 * {@code handlePlaceRecipe(id, recipe, false)} to place one set, then (b)
 * {@code handleInventoryMouseClick(id, 0, 0, QUICK_MOVE, player)} to quick-move that one set's result.
 * The count is {@code floor(resultMaxStack / yieldPerCraft)} so the total is up to one stack (a pickaxe
 * → 1, torches → 64); once the inventory runs out of materials the extra iterations simply no-op.
 *
 * <p><strong>2. Shift-click the output → craft the max from inventory</strong> (Bedrock-style;
 * vanilla only crafts the sets already in the grid). In {@code handleInventoryMouseClick} we (a)
 * {@code handlePlaceRecipe(id, recipe, true)} so the server re-fills the grid with as many sets as the
 * inventory allows, then (b) quick-move the result once — {@code AbstractContainerMenu#doClick} loops
 * {@code quickMoveStack} until the grid is depleted, crafting everything we just placed.
 *
 * <p><strong>3. Normal-click the output → craft to cursor and keep the grid stocked</strong>
 * (Bedrock-style; vanilla empties the grid after one craft). In {@code handleInventoryMouseClick} we
 * (a) let vanilla run the normal pickup click — {@code doClick} already crafts one set onto the cursor
 * and tops up a matching cursor stack — then (b) {@code handlePlaceRecipe(id, recipe, false)} to
 * re-place one set, so the ingredients stay on the table and the player can keep clicking the output to
 * pile crafts onto the cursor (up to a full stack, or until the inventory runs out).
 *
 * <p>Both output features need to know the recipe to re-place it. Rather than caching the book click,
 * {@link #bcc$resolveGridRecipe} looks the recipe up from whatever is currently in the grid via the
 * client recipe manager — so they work for a hand-filled grid too, matching Bedrock (whose grid is
 * inventory-backed and keeps crafting regardless of how it was filled).
 *
 * <p>{@link #bcc$reentrant} guards the internal vanilla calls so the features never trigger each other
 * (e.g. feature 1's quick-move must not be re-read as a feature-2 output click).
 *
 * <p><strong>Fragility note:</strong> depends on (a) the recipe book passing {@code shiftDown} as
 * {@code placeAll}, and (b) the crafting result living at slot index 0 of {@link CraftingMenu} /
 * {@link InventoryMenu}. Both are stable in vanilla 1.21.1 but are the things most likely to break on
 * a future MC update. See the README testing checklist — this needs in-game verification.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    /** Result slot index for both {@link CraftingMenu} (3x3) and {@link InventoryMenu} (2x2). */
    private static final int RESULT_SLOT_INDEX = 0;

    /** Set while we are issuing our own vanilla calls, so our injects skip them (no cross-triggering). */
    @Unique
    private boolean bcc$reentrant = false;

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void bcc$onHandlePlaceRecipe(int containerId, RecipeHolder<?> recipe, boolean shiftDown, CallbackInfo ci) {
        if (bcc$reentrant) {
            return; // one of our own internal calls -> let vanilla run unmodified
        }
        if (recipe == null) {
            return; // nothing to place; also lets the compiler treat recipe as non-null below
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) {
            return; // not the menu we were told about -> vanilla
        }

        boolean isCraftingTable = menu instanceof CraftingMenu;
        boolean isInventoryGrid = menu instanceof InventoryMenu;

        // The whole "should we craft a stack?" decision lives in the pure, unit-tested RecipeClickPolicy.
        // A false return covers: mod disabled, the core flag off, a normal (non-shift) click that
        // vanilla already handles, and any menu we don't touch (furnaces, smithing, stonecutter, ...).
        // The recipe book passes Screen.hasShiftDown() as the shiftDown argument.
        if (!RecipeClickPolicy.shouldCraftStack(
                Config.enableBedrockRecipeClickBehavior,
                Config.shiftClickRecipeCraftsStack,
                shiftDown,
                isCraftingTable,
                isInventoryGrid,
                Config.affectInventoryCrafting)) {
            return; // -> vanilla
        }

        // Craft enough single sets to fill one stack of the result (Bedrock-style). One craft yields
        // the recipe's output count; floor(maxStack / yield) crafts keep the total within a stack.
        ItemStack result = recipe.value().getResultItem(player.registryAccess());
        int crafts = RecipeClickPolicy.stackCraftCount(result.getCount(), result.getMaxStackSize());

        MultiPlayerGameMode self = (MultiPlayerGameMode) (Object) this;
        bcc$reentrant = true;
        try {
            for (int i = 0; i < crafts; i++) {
                // Place ONE set, then quick-move the result once -> crafts exactly that set's output.
                // When the inventory runs dry these two calls become harmless server-side no-ops.
                self.handlePlaceRecipe(containerId, recipe, false);
                self.handleInventoryMouseClick(containerId, RESULT_SLOT_INDEX, 0, ClickType.QUICK_MOVE, player);
            }
        } finally {
            bcc$reentrant = false;
        }

        if (BedrockCraftingControls.DEBUG) {
            BedrockCraftingControls.LOGGER.debug(
                    "[BCC] shift-click recipe -> crafted up to a stack ({} crafts, container {}, menu {})",
                    crafts,
                    containerId,
                    menu.getClass().getSimpleName());
        }

        ci.cancel(); // we fully handled the shift-click; skip vanilla's bulk placement.
    }

    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void bcc$onHandleInventoryMouseClick(
            int containerId, int slotId, int mouseButton, ClickType clickType, Player player, CallbackInfo ci) {
        if (bcc$reentrant) {
            return; // one of our own internal clicks -> let vanilla run unmodified
        }

        // Both output features act only on the result slot.
        if (slotId != RESULT_SLOT_INDEX) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) {
            return;
        }

        boolean isCraftingTable = menu instanceof CraftingMenu;
        boolean isInventoryGrid = menu instanceof InventoryMenu;
        boolean isQuickMove = clickType == ClickType.QUICK_MOVE;
        boolean isPickup = clickType == ClickType.PICKUP;

        boolean wantsCraftMax = RecipeClickPolicy.shouldCraftMaxOnOutput(
                Config.enableBedrockRecipeClickBehavior,
                Config.shiftClickOutputCraftsMax,
                isQuickMove,
                true,
                isCraftingTable,
                isInventoryGrid,
                Config.affectInventoryCrafting);
        boolean wantsRefill = RecipeClickPolicy.shouldRefillGridOnOutputClick(
                Config.enableBedrockRecipeClickBehavior,
                Config.clickOutputRefillsGrid,
                isPickup,
                true,
                isCraftingTable,
                isInventoryGrid,
                Config.affectInventoryCrafting);
        if (!wantsCraftMax && !wantsRefill) {
            return; // unrelated click -> vanilla (don't even touch the recipe manager)
        }

        // Empty output -> nothing to craft; let vanilla no-op so we don't place into an empty result.
        if (menu.getSlot(RESULT_SLOT_INDEX).getItem().isEmpty()) {
            return;
        }

        // Identify the recipe from whatever is currently in the grid. This works whether the grid was
        // loaded from the recipe book OR filled by hand (matching Bedrock, where the grid stays/refills
        // from the inventory regardless). If we can't identify it, fall back to vanilla.
        RecipeHolder<?> recipe = bcc$resolveGridRecipe(menu, player, isCraftingTable);
        if (recipe == null) {
            return;
        }

        MultiPlayerGameMode self = (MultiPlayerGameMode) (Object) this;

        // Feature 2 — shift-click (quick-move) the output: craft the max the inventory allows.
        if (wantsCraftMax) {
            bcc$reentrant = true;
            try {
                // 1) Re-fill the grid with as many ingredient sets as the inventory allows (vanilla placeAll).
                self.handlePlaceRecipe(containerId, recipe, true);
                // 2) Quick-move the result once. doClick loops quickMoveStack until the grid is depleted,
                //    so this crafts every set we just placed.
                self.handleInventoryMouseClick(
                        containerId, RESULT_SLOT_INDEX, mouseButton, ClickType.QUICK_MOVE, player);
            } finally {
                bcc$reentrant = false;
            }
            if (BedrockCraftingControls.DEBUG) {
                BedrockCraftingControls.LOGGER.debug(
                        "[BCC] shift-click output -> bulk-placed + crafted max (container {}, menu {})",
                        containerId,
                        menu.getClass().getSimpleName());
            }
            ci.cancel();
            return;
        }

        // Feature 3 — normal-click (pickup) the output: craft to the cursor, then re-stock the grid so
        // the player can keep clicking to pile crafts onto the cursor (Bedrock-style). Vanilla already
        // accumulates onto a matching cursor in doClick; it just never refills the grid.
        bcc$reentrant = true;
        try {
            // 1) Let vanilla do the normal click: craft one set onto the cursor (or top it up).
            self.handleInventoryMouseClick(containerId, RESULT_SLOT_INDEX, mouseButton, ClickType.PICKUP, player);
            // 2) Re-place one set so the ingredients stay on the table for the next click.
            self.handlePlaceRecipe(containerId, recipe, false);
        } finally {
            bcc$reentrant = false;
        }
        if (BedrockCraftingControls.DEBUG) {
            BedrockCraftingControls.LOGGER.debug(
                    "[BCC] click output -> crafted to cursor + re-stocked grid (container {}, menu {})",
                    containerId,
                    menu.getClass().getSimpleName());
        }
        ci.cancel();
    }

    /**
     * Resolve the crafting recipe from what is currently in the grid, using the client's recipe
     * manager. The grid occupies slots {@code 1..N} (the result is slot 0) for both the 3x3
     * {@link CraftingMenu} and the 2x2 {@link InventoryMenu}. Returns {@code null} if nothing matches
     * (e.g. an incomplete grid), in which case the caller leaves the click to vanilla.
     */
    @Unique
    private RecipeHolder<?> bcc$resolveGridRecipe(AbstractContainerMenu menu, Player player, boolean isCraftingTable) {
        int dim = isCraftingTable ? 3 : 2; // CraftingMenu = 3x3, InventoryMenu = 2x2
        List<ItemStack> grid = new ArrayList<>(dim * dim);
        for (int i = 0; i < dim * dim; i++) {
            grid.add(menu.getSlot(1 + i).getItem());
        }
        Level level = player.level();
        return level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(dim, dim, grid), level)
                .orElse(null);
    }
}
