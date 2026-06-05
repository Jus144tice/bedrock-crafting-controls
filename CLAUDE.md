# CLAUDE.md — Bedrock Crafting Controls

Navigation map for AI code-assist tools. Anchors are **symbol names** (classes, methods, fields,
config keys, Gradle task names), not line numbers — search for the anchor to jump to the code.

## ⚠️ Mandate: keep this file self-updating

**This file MUST be updated in the same session as any change that affects it — never deferred to a
later session.** If, during a task, you do any of the following, update the relevant section of this
CLAUDE.md before finishing the task (treat it as part of the change, not optional follow-up):

- add / rename / move / delete a file, class, method, config key, or Gradle task referenced here;
- change a vanilla hook point, Mixin target, or the slot-index / shift assumptions;
- bump a platform version (MC, NeoForge, Parchment, Gradle) or change the build flow;
- change the mod's behavior, scope (which menus are affected), or conventions/gotchas.

Keep edits surgical: fix the affected table row / anchor, don't rewrite the whole file. If a change
makes an anchor stale, the map is wrong and harmful — fixing it is required, not nice-to-have.

## Purpose

Client-only NeoForge QoL mod for **Minecraft Java 1.21.1** that recreates Bedrock-style crafting
controls in the vanilla crafting table. Three behavior changes, all in one Mixin class, all gated by
config and decided by the pure `RecipeClickPolicy`:
1. **Shift-click a recipe → craft up to a stack** of the result into the inventory (instead of
   vanilla's "bulk-fill the grid"). Count = `floor(resultMaxStack / yieldPerCraft)` (pickaxe → 1,
   torches → 64), bounded by available materials.
2. **Shift-click the output → craft the max** the inventory allows, Bedrock-style (instead of
   vanilla's "craft only the sets already in the grid").
3. **Normal-click the output → craft to cursor + keep the grid stocked**, so repeated clicks pile
   crafts onto the cursor up to a stack (instead of vanilla emptying the grid after one craft).

Normal-click on a *recipe* still just loads the grid (vanilla). No server install, no JEI/other-mod dependency.

## How the core features work (read this before touching the Mixin)

Both features call only stock vanilla client methods (`handlePlaceRecipe` →
`ServerboundPlaceRecipePacket`, `handleInventoryMouseClick` → `ServerboundContainerClickPacket`), so
they work on vanilla servers — the server stays authoritative and reconciles container state. Each
feature's yes/no decision is delegated to the pure, unit-tested `RecipeClickPolicy` (no Minecraft
deps). A `bcc$reentrant` flag guards the internal vanilla calls so the two features never trigger
each other.

**Feature 1 — `handlePlaceRecipe` hook (`shouldCraftStack`).** When shift is held on an affected
menu, the stack target `RecipeClickPolicy#stackCraftCount(result.getCount(), result.getMaxStackSize())`
is computed from the recipe's result (`recipe.value().getResultItem(player.registryAccess())`), then
**capped to the sets the inventory can supply** via `StackedContents#getBiggestCraftableStack` →
`RecipeClickPolicy#cappedCraftCount` (so no no-op packets are sent once materials run out; `0` → defer
to vanilla / ghost). We loop that many times:
1. `handlePlaceRecipe(id, recipe, false)` → places **one** ingredient set, then
2. `handleInventoryMouseClick(id, 0, 0, QUICK_MOVE, player)` → quick-moves that one set's result.
Vanilla placement is **all-or-nothing per recipe** (a missing ingredient clears the grid instead of
placing a partial set), so the loop can never craft the wrong item and never loses/dupes on a full
inventory — see [`docs/vanilla-hooks.md`](docs/vanilla-hooks.md).

Both output features below need the recipe to re-place it. We don't cache the book click; instead
`bcc$resolveGridRecipe` looks it up from whatever is **currently in the grid** (slots `1..N`, result
is slot 0) via the client recipe manager (`level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
CraftingInput.of(w, h, gridItems), level)`). So they work for a hand-filled grid too, matching Bedrock
(whose grid is inventory-backed). If nothing matches, they fall through to vanilla.

**Feature 2 — `handleInventoryMouseClick` QUICK_MOVE branch (`shouldCraftMaxOnOutput`).** When the
result slot (index 0) is shift-clicked on an affected menu (recipe resolved from the grid):
1. `handlePlaceRecipe(id, recipe, true)` → server re-fills the grid with as many sets as the
   inventory allows, then
2. `handleInventoryMouseClick(id, 0, 0, QUICK_MOVE, player)` → `AbstractContainerMenu#doClick` loops
   `quickMoveStack` until the grid is depleted, crafting everything just placed.

**Feature 3 — `handleInventoryMouseClick` PICKUP branch (`shouldRefillGridOnOutputClick`).** When the
result slot is **normal-clicked** (not shift) (recipe resolved from the grid):
1. Let vanilla run the pickup click — `doClick` crafts one set onto the cursor and (for a matching
   cursor) tops it up via `itemstack.grow(maxStack - count)`, then
2. `handlePlaceRecipe(id, recipe, false)` → re-places one set so the ingredients stay on the table.
Repeated clicks then keep crafting onto the cursor up to a stack (or until materials run out). The
accumulation is pure vanilla; the mod only re-stocks the grid.

**Fragility:** depends on (a) recipe book passing shift as the `placeAll` boolean, and (b) result
slot == index 0 with the grid at slots `1..N` for both `CraftingMenu` and `InventoryMenu`.

## Feature → file/symbol map

| Need to work on... | File | Symbol / anchor |
| --- | --- | --- |
| **Feature 1: shift-click recipe → craft a stack** | `src/main/java/com/bedrockcraftingcontrols/mixin/MultiPlayerGameModeMixin.java` | `bcc$onHandlePlaceRecipe` (`@Inject` at `handlePlaceRecipe` HEAD); the craft loop + `stackCraftCount`; `RESULT_SLOT_INDEX` |
| **Features 2 & 3: output clicks** | same file | `bcc$onHandleInventoryMouseClick` (`@Inject` at `handleInventoryMouseClick` HEAD): QUICK_MOVE branch = craft-max (feature 2), PICKUP branch = craft-to-cursor + re-stock (feature 3); recipe looked up from the grid via `bcc$resolveGridRecipe`; `bcc$reentrant` guard |
| **The intercept decisions (pure, testable)** | `src/main/java/com/bedrockcraftingcontrols/RecipeClickPolicy.java` | `shouldCraftStack(...)`, `shouldCraftMaxOnOutput(...)`, `shouldRefillGridOnOutputClick(...)` (enable/click/menu truth tables) and `stackCraftCount(yield, maxStack)` (the count math); no Minecraft deps |
| Mod entry / lifecycle | `src/main/java/com/bedrockcraftingcontrols/BedrockCraftingControls.java` | `BedrockCraftingControls(IEventBus, ModContainer)` ctor; `MOD_ID`; `DEBUG` (verbose-logging toggle); `LOGGER` |
| Config schema (on-disk spec) | `src/main/java/com/bedrockcraftingcontrols/Config.java` | `SPEC`; `ENABLE_BEDROCK_RECIPE_CLICK_BEHAVIOR`, `NORMAL_CLICK_LOADS_RECIPE_ONLY`, `SHIFT_CLICK_RECIPE_CRAFTS_STACK`, `SHIFT_CLICK_OUTPUT_CRAFTS_MAX`, `CLICK_OUTPUT_REFILLS_GRID`, `AFFECT_INVENTORY_CRAFTING` |
| Config values read by the Mixin | `Config.java` | cached `volatile` fields (`enableBedrockRecipeClickBehavior`, `shiftClickRecipeCraftsStack`, `shiftClickOutputCraftsMax`, `clickOutputRefillsGrid`, `affectInventoryCrafting`); populated by `bake()` |
| Config (re)load wiring | `Config.java` + `BedrockCraftingControls.java` | `Config.onConfigEvent`; `modBus.addListener(... ModConfigEvent.Loading / .Reloading ...)` in the ctor |
| Register Mixin with the loader | `src/main/resources/bedrockcraftingcontrols.mixins.json` | `"client": ["MultiPlayerGameModeMixin"]` (client-only list); `"package"` |
| Mod metadata (loaded by NeoForge) | `src/main/templates/META-INF/neoforge.mods.toml` | `displayName`/`authors`/`license`/`version` (all `${...}` from `gradle.properties`); `logoFile`; `displayURL`; `[[mixins]]`; `[[dependencies.bedrockcraftingcontrols]]`; `side = "CLIENT"` |
| Mod icon (shown in mod lists) | `src/main/resources/bedrockcraftingcontrols.png` | referenced by `logoFile` in the toml; regenerate via `tools/make_icon.py` (needs Python+Pillow; 3x3 grid, one emerald "1" cell) |
| Resource-pack stub | `src/main/resources/pack.mcmeta` | `pack_format` (34 for 1.21.1) |

## Tests

JUnit 5, run via the moddev `unitTest` harness (NeoForge runtime on the test classpath). The Mixin
itself can't be unit-tested without a live client, so the testable logic was extracted into
`RecipeClickPolicy`. Run with `.\gradlew.bat test`.

| Need to work on... | File | Symbol / anchor |
| --- | --- | --- |
| Decision-logic tests (the truth tables + count math) | `src/test/java/com/bedrockcraftingcontrols/RecipeClickPolicyTest.java` | `exhaustiveTruthTable` (feature 1, 64 cases); `OutputCraftsMax.exhaustive` (feature 2, 128 cases); `OutputRefillsGrid.exhaustive` (feature 3, 128 cases); `StackCraftCount` (the stack-count math); the `@Nested` scenario classes |
| Config schema/defaults tests | `src/test/java/com/bedrockcraftingcontrols/ConfigTest.java` | `specDefaults`, `valuePaths`, `cachedDefaultsMirrorSpec` (asserts via `getDefault()`/`getPath()`, no config load) |
| Test harness + JUnit deps | `build.gradle` | `neoForge { unitTest { enable(); testedMod } }`; `testImplementation`/`testRuntimeOnly`; `tasks.named('test')` |

## Build / version map

| Need to change... | File | Anchor |
| --- | --- | --- |
| Platform versions (MC / NeoForge / Parchment / ranges) | `gradle.properties` | `minecraft_version`, `neo_version`, `parchment_version`, `*_version_range` |
| Mod id / name / version / group / license | `gradle.properties` | `mod_id`, `mod_version`, `mod_group_id`, `mod_license` (single source of truth) |
| Build logic, toolchain (Java 21), runs | `build.gradle` | `neoForge { version / parchment / runs.client / mods }`; `java.toolchain` |
| Formatting (palantir-java-format) | `build.gradle` | `spotless { java { ... } }`; **auto-runs**: `compileJava` `dependsOn 'spotlessApply'`, so `build` formats before it compiles. `spotlessCheck` stays as a standalone dry-run gate |
| How `${...}` in the toml gets filled | `build.gradle` | `generateModMetadata` task (expands `gradle.properties` into the toml template) |
| Gradle version | `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl` (8.12.1) |
| License | `LICENSE` + `gradle.properties` | Apache-2.0 text in `LICENSE`; `mod_license=Apache-2.0` flows into the toml via `${mod_license}` |
| CI (build + test on push/PR) | `.github/workflows/build.yml` | sets up JDK 21, runs `./gradlew build`, uploads the jar artifact |

> Note: `neoforge.mods.toml` lives in `src/main/templates/` (not `resources/`) because it uses
> `${...}` placeholders expanded by `generateModMetadata`. The built/expanded copy lands in the jar
> under `META-INF/`.

## Reading vanilla source (don't commit it)

The vanilla methods, slot layouts, and behaviors this mod hooks are written up in our own words in
[`docs/vanilla-hooks.md`](docs/vanilla-hooks.md) — read that first; it usually saves a decompile.

The decompiled **Minecraft + NeoForge** sources themselves are produced locally by the moddev plugin —
do **not** vendor them into this repo (proprietary MC code; large; reproducible). To grep them, the
moddev cache holds a sources jar at:

```
~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*.jar
```

(Unzip the one whose listing contains `MultiPlayerGameMode.java`.) The NeoForge-only API sources
(e.g. `ModConfigSpec`) are in the `neoforge-<ver>-sources.jar` under
`~/.gradle/caches/modules-2/.../net.neoforged/neoforge/`. IDE "go to definition" also resolves these
after a Gradle sync. The hook points below were verified this way.

## Vanilla hook points (Mojmaps 1.21.1) — verified against decompiled sources

| Vanilla symbol | Class | Used for |
| --- | --- | --- |
| `handlePlaceRecipe(int, RecipeHolder<?>, boolean)` | `net.minecraft.client.multiplayer.MultiPlayerGameMode` | feature-1 `@Inject` target; bool arg == shift/`placeAll`; **only sends a packet** (server does placement) |
| `handleInventoryMouseClick(int, int, int, ClickType, Player)` | same | feature-2/3 `@Inject` target; **returns `void`** in 1.21.1 (so a cancellable `CallbackInfo` works); QUICK_MOVE = feature 2, PICKUP = feature 3 |
| `doClick` QUICK_MOVE loop | `net.minecraft.world.inventory.AbstractContainerMenu` | one shift-click on the result loops `quickMoveStack` until the grid is depleted → crafts all sets in the grid |
| `doClick` PICKUP result branch | `net.minecraft.world.inventory.AbstractContainerMenu` | clicking the result with a matching cursor does `cursor.grow(maxStack - count)` → vanilla already accumulates onto the cursor; feature 3 only re-stocks the grid so this can repeat |
| `getRecipeFor(RecipeType, RecipeInput, Level)` | `net.minecraft.world.item.crafting.RecipeManager` (client: `Level#getRecipeManager`) | resolve the recipe from grid contents (`CraftingInput.of(w, h, slots)`) for features 2 & 3; works on the client since recipes are synced |
| `getBiggestCraftableStack(RecipeHolder, IntList)` | `net.minecraft.world.entity.player.StackedContents` (fill via `Inventory#fillStackedContents`) | count how many sets the inventory can craft → caps feature 1's loop; client-usable, counts the main inventory only |
| `recipeClicked` `canCraft` gate | `net.minecraft.recipebook.ServerPlaceRecipe` | proves placement is all-or-nothing: missing an ingredient → `clearGrid()` + ghost, never a partial set (why the loop is safe) |
| `RESULT_SLOT = 0` | `net.minecraft.world.inventory.CraftingMenu` / `InventoryMenu` | result slot index (both = 0) |
| `handlePlaceRecipe(..., Screen.hasShiftDown())` call site | `net.minecraft.client.gui.screens.recipebook.RecipeBookComponent` | proves shift maps to the bool arg |

## Conventions / gotchas

- **Client-only.** `@Mod(dist = Dist.CLIENT)`; Mixin is in the `client` list. Don't add server-side code.
- **No refmap.** Dev and production both run Mojmaps, so `mixins.json` deliberately omits a refmap.
  Mixin member names must match Mojmaps exactly.
- **Don't touch other menus.** The `instanceof CraftingMenu` / `InventoryMenu` flags feed
  `RecipeClickPolicy` (`shouldCraftStack`, `shouldCraftMaxOnOutput`, `shouldRefillGridOnOutputClick`),
  which is what keeps furnaces/smithing/stonecutter out. The Mixin computes the flags; the policy
  makes the call. Keep both, and mirror any rule change in `RecipeClickPolicyTest`.
- **The features must not trigger each other.** Both injects bail when `bcc$reentrant` is set, and all
  wrap their internal `handlePlaceRecipe` / `handleInventoryMouseClick` calls in it. Without this,
  feature 1's result quick-move would be re-read as a feature-2 output click (and vice-versa), and
  feature 3's internal pickup would re-enter itself.
- **Features 2 & 3 need the recipe.** They get it from `bcc$resolveGridRecipe`, which queries the
  client recipe manager with the current grid contents (`CraftingInput.of(w, h, slots 1..N)`). This
  works for book-loaded **and** hand-filled grids (Bedrock-accurate). No match → vanilla output click.
  (Don't reintroduce a book-click cache — it broke the hand-filled case.)
- **Runtime logging quiet by default.** Use `LOGGER.debug` gated by `DEBUG` (constant in the main class).
- **Behavior is unverified in-game** — unit tests cover the decision logic, but the two-packet craft
  sequence still needs the in-game testing pass in `README.md` (esp. multiplayer desync). Unit tests
  passing ≠ in-game verified; don't claim the latter until then.
- **Build = format + compile + test, one command.** `.\gradlew.bat build` (needs JDK 21) auto-runs
  `spotlessApply`, compiles, runs the JUnit suite, and jars → `build/libs/bedrockcraftingcontrols-<version>.jar`.
  No separate format step anymore. `.\gradlew.bat test` runs just the tests (still auto-formats first);
  `.\gradlew.bat spotlessCheck` is an optional standalone dry-run of the format gate.
- **End every change session by committing and pushing** to the GitHub remote (`origin`, branch
  `main`): `git add -A && git commit && git push`. The commit footer must include the
  `Co-Authored-By: Claude` line. This is a standing instruction — treat it as part of finishing.
