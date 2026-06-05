# Vanilla hook reference (Mojmaps, MC 1.21.1 / NeoForge 21.1.233)

Our-words notes on the vanilla methods, slot layouts, and behaviors this mod depends on. This exists
so we don't have to re-decompile every session.

> **We do not vendor Minecraft's decompiled sources** (Mojang's proprietary code; the Apache-2.0/Modrinth
> repo can't carry it; it's huge and regenerated locally anyway). This file records the *facts* —
> signatures, indices, behaviors — in our own words instead. To read the actual source, see
> [How to read vanilla source](#how-to-read-vanilla-source) below.

Everything here was verified against the moddev-decompiled sources for this exact version. If you bump
`minecraft_version` / `neo_version` in `gradle.properties`, re-verify each row.

---

## Client recipe-click entry points — `net.minecraft.client.multiplayer.MultiPlayerGameMode`

| Member | Signature / fact | Notes |
| --- | --- | --- |
| `handlePlaceRecipe` | `void handlePlaceRecipe(int containerId, RecipeHolder<?> recipe, boolean placeAll)` | **Only sends a packet** (`ServerboundPlaceRecipePacket`); the server does the actual placement and syncs the grid back. `placeAll` is `true` when Shift was held (the recipe book passes `Screen.hasShiftDown()`), `false` for a normal click (place one set). Feature 1's `@Inject` target. |
| `handleInventoryMouseClick` | `void handleInventoryMouseClick(int containerId, int slotId, int mouseButton, ClickType clickType, Player player)` | Runs `menu.clicked(...)` locally (client prediction) then sends `ServerboundContainerClickPacket`. **Returns `void`** in 1.21.1, so a cancellable Mixin `@Inject` with `CallbackInfo` works (no `CallbackInfoReturnable`). Features 2 & 3's `@Inject` target. |

The recipe book's call site is in
`net.minecraft.client.gui.screens.recipebook.RecipeBookComponent` — it calls
`handlePlaceRecipe(..., Screen.hasShiftDown())`, which is what proves Shift maps to the `placeAll`
boolean.

## Menu / slot layout

| Menu | Class | Result slot | Crafting grid slots | Dimensions |
| --- | --- | --- | --- | --- |
| 3×3 crafting table | `net.minecraft.world.inventory.CraftingMenu` | `RESULT_SLOT = 0` | menu slots `1..9` (row-major) | 3×3 |
| 2×2 inventory grid | `net.minecraft.world.inventory.InventoryMenu` | `RESULT_SLOT = 0` | menu slots `1..4` (row-major) | 2×2 (`CRAFT_SLOT_START = 1`, `CRAFT_SLOT_COUNT = 4`) |

Both put the result at index 0 and the grid immediately after at index 1. Reading `menu.getSlot(1+i)`
in order yields row-major grid contents — directly usable as `CraftingInput.of(dim, dim, items)`.

## Output-slot click behavior — `net.minecraft.world.inventory.AbstractContainerMenu#doClick`

- **QUICK_MOVE on the result slot** (shift-click): `doClick` runs `quickMoveStack` in a `while` loop
  *as long as the result keeps matching* — so one shift-click crafts **every set currently in the
  grid**, then stops when the grid can't produce the same result. (This is why feature 2 only needs to
  fill the grid once, then issue a single quick-move.)
- **PICKUP on the result slot** (normal click) with a **matching item on the cursor**: the result
  branch does `cursor.grow(maxStackSize - cursorCount)` via `tryRemove(...)` — i.e. **vanilla already
  accumulates onto the cursor** up to a full stack. The only reason Java stops you is the grid empties
  after one craft. (This is why feature 3 just needs to re-stock the grid; the pile-up is pure
  vanilla.)

## Recipe lookup from grid contents (client-side)

Used by features 2 & 3 to identify the recipe from whatever is in the grid (so they work for
hand-filled grids, not just book-loaded ones).

| Member | Signature / fact | Notes |
| --- | --- | --- |
| `Level#getRecipeManager` | `RecipeManager getRecipeManager()` | On the client, `ClientLevel` returns `connection.getRecipeManager()`. Recipes are synced to the client (`ClientboundUpdateRecipesPacket`), so lookups work client-side. Reachable as `player.level().getRecipeManager()`. |
| `RecipeManager#getRecipeFor` | `<I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, Level level)` | Returns the single matching recipe for the exact input, or empty for an incomplete grid (→ we fall back to vanilla). |
| `CraftingInput#of` | `static CraftingInput of(int width, int height, List<ItemStack> items)` | Row-major item list. Not deprecated in 1.21.1. |
| `RecipeType.CRAFTING` | `RecipeType<CraftingRecipe> CRAFTING` | The crafting recipe type constant. |
| `Recipe#getResultItem` | `ItemStack getResultItem(HolderLookup.Provider registries)` | Result `ItemStack`: `getCount()` is the per-craft yield, `getMaxStackSize()` the stack cap. Used by feature 1's `stackCraftCount`. Get `registries` from `player.registryAccess()` (declared on `Entity`). Not deprecated in 1.21.1. |
| `StackedContents#getBiggestCraftableStack` | `int getBiggestCraftableStack(RecipeHolder<?> recipe, @Nullable IntList)` | Max number of **sets** (crafts) the contents can supply. Fill it client-side with `player.getInventory().fillStackedContents(stackedContents)` (counts the 36 main slots only). Feature 1 caps its loop to this so it stops once materials run out. |

## Why feature 1's craft loop is safe (no wrong item, no loss/dupe)

Feature 1 loops "place one set + quick-move the result." Each placement is a `ServerboundPlaceRecipePacket`
the server runs through `net.minecraft.recipebook.ServerPlaceRecipe#recipeClicked`, which is **gated
and all-or-nothing**:

- It only proceeds `if (testClearGrid() || isCreative())` — `testClearGrid()` returns `false` when the
  grid's current contents couldn't fit back into the inventory, so it bails rather than risk dropping
  items.
- It then checks `stackedContents.canCraft(recipe, null)` — "do I have a *full* set?". If **any**
  ingredient is missing it takes the else branch: `clearGrid()` (empties the grid back to inventory)
  and sends a ghost-recipe packet. It **never** places a partial set.

Consequences for the loop:
- **One ingredient runs out →** the next placement places nothing and *clears* the grid, so the
  result-slot quick-move has nothing to craft. A half-empty grid can never accidentally match a
  different recipe and craft the wrong thing.
- **Inventory full →** the quick-move can't move the result, so `ResultSlot#onTake` never fires and the
  ingredients are not consumed. No craft, no loss, no duplication (and `testClearGrid` prevents drops).

So the loop relies on the server's guards for correctness; the client-side cap
(`getBiggestCraftableStack`) is purely a packet-count optimization.

## Why this is network-safe

Every feature calls only the two stock client methods above, which emit only stock vanilla packets
(`ServerboundPlaceRecipePacket`, `ServerboundContainerClickPacket`). The server stays authoritative
and reconciles container state, so no server-side install is needed and it works on vanilla servers.
Local client prediction may briefly mis-show during very fast clicking, but the server resyncs.

---

## How to read vanilla source

The moddev plugin decompiles Minecraft + NeoForge locally. To grep the real source:

```
~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*.jar
```

Unzip the one whose listing contains `MultiPlayerGameMode.java`. NeoForge-only API sources (e.g.
`ModConfigSpec`) live in `neoforge-<ver>-sources.jar` under
`~/.gradle/caches/modules-2/.../net.neoforged/neoforge/`. IDE "go to definition" also resolves these
after a Gradle sync.
