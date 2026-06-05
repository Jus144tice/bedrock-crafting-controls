package com.bedrockcraftingcontrols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RecipeClickPolicy} — the pure brain of the mod. These run with no Minecraft
 * client, which is exactly why the decision logic was pulled out of the Mixin.
 */
class RecipeClickPolicyTest {

    // Convenience: the canonical "everything is ready to craft a stack" call (table, enabled, shift held).
    private static boolean craftingTableShift(boolean enabled, boolean coreFlag) {
        return RecipeClickPolicy.shouldCraftStack(
                enabled,
                coreFlag,
                /* shiftDown= */ true,
                /* isCraftingTable= */ true,
                /* isInventoryGrid= */ false,
                /* affectInventoryCrafting= */ false);
    }

    @Nested
    @DisplayName("the happy path")
    class HappyPath {

        @Test
        @DisplayName("shift-clicking a recipe at a crafting table crafts a stack")
        void shiftAtCraftingTableCraftsStack() {
            assertTrue(craftingTableShift(true, true));
        }

        @Test
        @DisplayName("a crafting table is eligible regardless of the inventory-grid opt-in")
        void craftingTableIgnoresInventoryOptIn() {
            assertTrue(RecipeClickPolicy.shouldCraftStack(true, true, true, true, false, false));
            assertTrue(RecipeClickPolicy.shouldCraftStack(true, true, true, true, false, true));
        }
    }

    @Nested
    @DisplayName("the config switches turn it off")
    class Switches {

        @Test
        @DisplayName("the master switch suppresses everything")
        void masterSwitchOff() {
            assertFalse(craftingTableShift(false, true));
        }

        @Test
        @DisplayName("the core feature flag suppresses everything")
        void coreFlagOff() {
            assertFalse(craftingTableShift(true, false));
        }
    }

    @Nested
    @DisplayName("non-shift clicks are left to vanilla")
    class NormalClick {

        @Test
        @DisplayName("a normal (non-shift) click never crafts, even at a crafting table")
        void normalClickIsVanilla() {
            assertFalse(RecipeClickPolicy.shouldCraftStack(true, true, false, true, false, true));
        }
    }

    @Nested
    @DisplayName("which menus are affected")
    class MenuScope {

        @Test
        @DisplayName("the 2x2 inventory grid is NOT affected by default")
        void inventoryGridOptedOutByDefault() {
            assertFalse(RecipeClickPolicy.shouldCraftStack(true, true, true, false, true, false));
        }

        @Test
        @DisplayName("the 2x2 inventory grid IS affected once opted in")
        void inventoryGridOptedIn() {
            assertTrue(RecipeClickPolicy.shouldCraftStack(true, true, true, false, true, true));
        }

        @Test
        @DisplayName("a menu that is neither table nor inventory grid is never touched")
        void unknownMenuIsVanilla() {
            // e.g. furnace / smithing table / stonecutter: both menu flags are false.
            assertFalse(RecipeClickPolicy.shouldCraftStack(true, true, true, false, false, true));
        }
    }

    @Nested
    @DisplayName("shift-clicking the output crafts the max")
    class OutputCraftsMax {

        // Canonical "ready to craft max" call: quick-move on the result slot of a crafting table.
        private boolean call(boolean enabled, boolean maxFlag, boolean quickMove, boolean resultSlot) {
            return RecipeClickPolicy.shouldCraftMaxOnOutput(
                    enabled, maxFlag, quickMove, resultSlot, /* table= */ true, /* grid= */ false, false);
        }

        @Test
        @DisplayName("quick-moving the result at a crafting table crafts max")
        void happyPath() {
            assertTrue(call(true, true, true, true));
        }

        @Test
        @DisplayName("a non-quick-move (e.g. a plain pickup) click is left to vanilla")
        void onlyQuickMove() {
            assertFalse(call(true, true, false, true));
        }

        @Test
        @DisplayName("only the result slot triggers it; other slots are left to vanilla")
        void onlyResultSlot() {
            assertFalse(call(true, true, true, false));
        }

        @Test
        @DisplayName("the master switch and the output flag each suppress it")
        void switchesOff() {
            assertFalse(call(false, true, true, true));
            assertFalse(call(true, false, true, true));
        }

        @Test
        @DisplayName("the 2x2 inventory grid follows the opt-in, the table never needs it")
        void menuScope() {
            // inventory grid: off by default, on once opted in
            assertFalse(RecipeClickPolicy.shouldCraftMaxOnOutput(true, true, true, true, false, true, false));
            assertTrue(RecipeClickPolicy.shouldCraftMaxOnOutput(true, true, true, true, false, true, true));
            // a crafting table is eligible regardless of the inventory opt-in
            assertTrue(RecipeClickPolicy.shouldCraftMaxOnOutput(true, true, true, true, true, false, false));
            assertTrue(RecipeClickPolicy.shouldCraftMaxOnOutput(true, true, true, true, true, false, true));
            // neither table nor grid (furnace/smithing/...) -> never
            assertFalse(RecipeClickPolicy.shouldCraftMaxOnOutput(true, true, true, true, false, false, true));
        }

        @Test
        @DisplayName("the full 128-case truth table matches the documented specification")
        void exhaustive() {
            boolean[] b = {false, true};
            for (boolean enabled : b) {
                for (boolean maxFlag : b) {
                    for (boolean quick : b) {
                        for (boolean result : b) {
                            for (boolean table : b) {
                                for (boolean grid : b) {
                                    for (boolean affectGrid : b) {
                                        boolean expected = enabled
                                                && maxFlag
                                                && quick
                                                && result
                                                && (table || (grid && affectGrid));
                                        boolean actual = RecipeClickPolicy.shouldCraftMaxOnOutput(
                                                enabled, maxFlag, quick, result, table, grid, affectGrid);
                                        assertEquals(
                                                expected,
                                                actual,
                                                () -> "enabled=" + enabled + " maxFlag=" + maxFlag + " quick=" + quick
                                                        + " result=" + result + " table=" + table + " grid=" + grid
                                                        + " affectGrid=" + affectGrid);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the full 64-case truth table matches the documented specification")
    void exhaustiveTruthTable() {
        boolean[] bools = {false, true};
        for (boolean enabled : bools) {
            for (boolean coreFlag : bools) {
                for (boolean shift : bools) {
                    for (boolean table : bools) {
                        for (boolean grid : bools) {
                            for (boolean affectGrid : bools) {
                                boolean expected = enabled && coreFlag && shift && (table || (grid && affectGrid));
                                boolean actual = RecipeClickPolicy.shouldCraftStack(
                                        enabled, coreFlag, shift, table, grid, affectGrid);
                                assertEquals(
                                        expected,
                                        actual,
                                        () -> "enabled=" + enabled + " coreFlag=" + coreFlag + " shift=" + shift
                                                + " table=" + table + " grid=" + grid + " affectGrid=" + affectGrid);
                            }
                        }
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("normal-clicking the output keeps the grid stocked")
    class OutputRefillsGrid {

        // Canonical "ready to re-stock" call: a normal pickup on the result slot of a crafting table.
        private boolean call(boolean enabled, boolean refillFlag, boolean pickup, boolean resultSlot) {
            return RecipeClickPolicy.shouldRefillGridOnOutputClick(
                    enabled, refillFlag, pickup, resultSlot, /* table= */ true, /* grid= */ false, false);
        }

        @Test
        @DisplayName("a normal pickup on the result at a crafting table re-stocks the grid")
        void happyPath() {
            assertTrue(call(true, true, true, true));
        }

        @Test
        @DisplayName("a non-pickup (e.g. a shift quick-move) click is left to vanilla")
        void onlyPickup() {
            assertFalse(call(true, true, false, true));
        }

        @Test
        @DisplayName("only the result slot triggers it; other slots are left to vanilla")
        void onlyResultSlot() {
            assertFalse(call(true, true, true, false));
        }

        @Test
        @DisplayName("the master switch and the refill flag each suppress it")
        void switchesOff() {
            assertFalse(call(false, true, true, true));
            assertFalse(call(true, false, true, true));
        }

        @Test
        @DisplayName("the 2x2 inventory grid follows the opt-in, the table never needs it")
        void menuScope() {
            assertFalse(RecipeClickPolicy.shouldRefillGridOnOutputClick(true, true, true, true, false, true, false));
            assertTrue(RecipeClickPolicy.shouldRefillGridOnOutputClick(true, true, true, true, false, true, true));
            assertTrue(RecipeClickPolicy.shouldRefillGridOnOutputClick(true, true, true, true, true, false, false));
            assertFalse(RecipeClickPolicy.shouldRefillGridOnOutputClick(true, true, true, true, false, false, true));
        }

        @Test
        @DisplayName("the full 128-case truth table matches the documented specification")
        void exhaustive() {
            boolean[] b = {false, true};
            for (boolean enabled : b) {
                for (boolean refillFlag : b) {
                    for (boolean pickup : b) {
                        for (boolean result : b) {
                            for (boolean table : b) {
                                for (boolean grid : b) {
                                    for (boolean affectGrid : b) {
                                        boolean expected = enabled
                                                && refillFlag
                                                && pickup
                                                && result
                                                && (table || (grid && affectGrid));
                                        boolean actual = RecipeClickPolicy.shouldRefillGridOnOutputClick(
                                                enabled, refillFlag, pickup, result, table, grid, affectGrid);
                                        assertEquals(
                                                expected,
                                                actual,
                                                () -> "enabled=" + enabled + " refillFlag=" + refillFlag + " pickup="
                                                        + pickup + " result=" + result + " table=" + table + " grid="
                                                        + grid + " affectGrid=" + affectGrid);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("how many crafts fill one stack")
    class StackCraftCount {

        @Test
        @DisplayName("a tool that yields 1 and stacks to 1 -> a single craft")
        void singleNonStackable() {
            assertEquals(1, RecipeClickPolicy.stackCraftCount(1, 1)); // stone pickaxe
        }

        @Test
        @DisplayName("a recipe that yields 4 into a 64-stack -> 16 crafts (a full stack)")
        void torchesAndPlanksAndStairs() {
            // torches (4/craft), planks (4/craft), stairs (4/craft) all -> 16 crafts = 64 items
            assertEquals(16, RecipeClickPolicy.stackCraftCount(4, 64));
        }

        @Test
        @DisplayName("a recipe that yields 6 into a 64-stack -> 10 crafts (60, the most that fits)")
        void slabs() {
            // 64 / 6 = 10 (floor) -> 60 slabs, never overflowing the stack
            assertEquals(10, RecipeClickPolicy.stackCraftCount(6, 64));
        }

        @Test
        @DisplayName("a recipe that yields 1 into a 64-stack -> 64 crafts (e.g. a chest)")
        void singleYieldStackable() {
            assertEquals(64, RecipeClickPolicy.stackCraftCount(1, 64));
        }

        @Test
        @DisplayName("smaller stack sizes are respected (yield 3 into a 16-stack -> 5 crafts)")
        void smallerStack() {
            assertEquals(5, RecipeClickPolicy.stackCraftCount(3, 16)); // 15 items, <= 16
        }

        @Test
        @DisplayName("never overflows the stack: yield * count is always within one stack")
        void neverOverflows() {
            int[] yields = {1, 2, 3, 4, 6, 8, 9};
            int[] maxes = {1, 16, 64};
            for (int y : yields) {
                for (int m : maxes) {
                    int crafts = RecipeClickPolicy.stackCraftCount(y, m);
                    assertTrue(crafts >= 1, "always at least one craft");
                    assertTrue(crafts * y <= Math.max(y, m), "yield*crafts stays within a stack");
                }
            }
        }

        @Test
        @DisplayName("special/unknown results (0 yield or 0 stack) fall back to a single craft")
        void degenerateInputs() {
            assertEquals(1, RecipeClickPolicy.stackCraftCount(0, 64));
            assertEquals(1, RecipeClickPolicy.stackCraftCount(4, 0));
            assertEquals(1, RecipeClickPolicy.stackCraftCount(-3, 64));
        }
    }

    @Nested
    @DisplayName("capping the craft count to what the inventory can afford")
    class CappedCraftCount {

        @Test
        @DisplayName("plenty of materials -> craft the whole stack target")
        void enoughMaterials() {
            assertEquals(16, RecipeClickPolicy.cappedCraftCount(16, 20)); // afford 20, only need 16
        }

        @Test
        @DisplayName("short on materials -> craft only what's affordable (no wasted no-ops)")
        void shortOnMaterials() {
            assertEquals(10, RecipeClickPolicy.cappedCraftCount(16, 10)); // a stack wants 16, can do 10
            assertEquals(3, RecipeClickPolicy.cappedCraftCount(64, 3));
        }

        @Test
        @DisplayName("exact materials -> craft exactly that many")
        void exactMaterials() {
            assertEquals(16, RecipeClickPolicy.cappedCraftCount(16, 16));
        }

        @Test
        @DisplayName("nothing affordable -> 0 (Mixin then defers to vanilla / ghost recipe)")
        void nothingAffordable() {
            assertEquals(0, RecipeClickPolicy.cappedCraftCount(16, 0));
            assertEquals(0, RecipeClickPolicy.cappedCraftCount(16, -1));
            assertEquals(0, RecipeClickPolicy.cappedCraftCount(0, 5));
        }

        @Test
        @DisplayName("never exceeds either bound")
        void neverExceedsEitherBound() {
            int[] targets = {1, 4, 16, 64};
            int[] affords = {0, 1, 5, 16, 64, 999};
            for (int t : targets) {
                for (int a : affords) {
                    int c = RecipeClickPolicy.cappedCraftCount(t, a);
                    assertTrue(c >= 0, "never negative");
                    assertTrue(c <= t, "never more than the stack target");
                    assertTrue(c <= Math.max(0, a), "never more than affordable");
                }
            }
        }
    }

    @Nested
    @DisplayName("place-all cycles needed to drain the inventory")
    class PlaceAllCycles {

        @Test
        @DisplayName("a stack-or-less fits in one cycle")
        void oneCycle() {
            assertEquals(1, RecipeClickPolicy.placeAllCycles(1, 64));
            assertEquals(1, RecipeClickPolicy.placeAllCycles(64, 64));
        }

        @Test
        @DisplayName("more than one grid's worth needs multiple cycles (the oak-logs case)")
        void multipleCycles() {
            assertEquals(2, RecipeClickPolicy.placeAllCycles(65, 64)); // 1 over a stack -> 2 cycles
            assertEquals(2, RecipeClickPolicy.placeAllCycles(128, 64)); // 128 logs of planks -> 2
            assertEquals(4, RecipeClickPolicy.placeAllCycles(200, 64)); // a "ton" of logs -> 4
        }

        @Test
        @DisplayName("a smaller limiting-ingredient stack means more cycles")
        void smallerPerCycleCap() {
            assertEquals(3, RecipeClickPolicy.placeAllCycles(40, 16)); // ceil(40/16) = 3
        }

        @Test
        @DisplayName("nothing affordable -> 0 cycles")
        void nothingAffordable() {
            assertEquals(0, RecipeClickPolicy.placeAllCycles(0, 64));
            assertEquals(0, RecipeClickPolicy.placeAllCycles(-5, 64));
        }

        @Test
        @DisplayName("a degenerate per-cycle cap is clamped to at least 1")
        void degeneratePerCycle() {
            assertEquals(100, RecipeClickPolicy.placeAllCycles(100, 0));
            assertEquals(100, RecipeClickPolicy.placeAllCycles(100, -3));
        }

        @Test
        @DisplayName("always enough cycles to cover everything, never wildly too many")
        void coversWithoutOverkill() {
            int[] affords = {1, 5, 63, 64, 65, 128, 200, 999};
            int[] caps = {1, 16, 64};
            for (int a : affords) {
                for (int cap : caps) {
                    int cycles = RecipeClickPolicy.placeAllCycles(a, cap);
                    assertTrue((long) cycles * cap >= a, "cycles*cap covers the affordable amount");
                    assertTrue((long) (cycles - 1) * cap < a, "no wasted extra cycle");
                }
            }
        }
    }
}
