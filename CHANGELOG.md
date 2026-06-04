# Changelog

All notable changes to Bedrock Crafting Controls are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Bedrock-style **normal-click the crafting output → craft onto the cursor and keep the grid
  stocked**. The mod lets vanilla craft one set onto the cursor (vanilla already tops up a matching
  cursor stack), then re-places one ingredient set so the table stays loaded — so repeated clicks pile
  crafts onto the cursor up to a full stack (or until materials run out). New config flag
  `clickOutputRefillsGrid` (default `true`) and pure, unit-tested
  `RecipeClickPolicy#shouldRefillGridOnOutputClick` (128-case truth table). Implemented as a PICKUP
  branch in the existing `handleInventoryMouseClick` `@Inject`.
- Bedrock-style **shift-click the crafting output → craft the maximum** the inventory allows. The mod
  re-fills the grid from your inventory (vanilla "place all"), then quick-moves the result once —
  vanilla's quick-move loops until the grid is depleted, so it crafts every set. Implemented via a
  second `@Inject` on `MultiPlayerGameMode#handleInventoryMouseClick`. Previously
  `shiftClickOutputCraftsMax` was a no-op documentation flag; it now drives this behavior.
  `RecipeClickPolicy#shouldCraftMaxOnOutput` (pure, unit-tested) plus a 128-case truth-table test.
- Both output features identify the recipe by **looking it up from the current grid contents** (client
  recipe manager + `CraftingInput`), so they work for a **hand-filled grid**, not just one loaded from
  the recipe book — matching Bedrock, whose grid is inventory-backed and keeps crafting regardless of
  how it was filled. No match (incomplete grid) → vanilla.

### Changed
- **Shift-click a recipe now crafts up to a _stack_ of the result** (Bedrock-style), not just one.
  The craft count is `floor(resultMaxStackSize / yieldPerCraft)` (pickaxe → 1, torches → 64, stairs →
  64), bounded by available materials. Added `RecipeClickPolicy#stackCraftCount` (pure, unit-tested).
  **Config key renamed** `shiftClickRecipeCraftsOne` → `shiftClickRecipeCraftsStack` (the old key in a
  pre-existing config is ignored and regenerated at the new name with its default).
- `affectInventoryCrafting` now extends **all three** Bedrock behaviors (craft-a-stack, craft-max, and
  keep-grid-stocked) to the 2×2 inventory grid.

## [1.0.0] - 2026-06-02

### Added
- Initial release for **Minecraft 1.21.1 / NeoForge 21.1.x**, client-only.
- Bedrock-style **shift-click a recipe** in the crafting-table recipe book now crafts **exactly one**
  result into the player inventory instead of bulk-filling the grid. Implemented via a single client
  Mixin on `MultiPlayerGameMode#handlePlaceRecipe` (places one set, then quick-moves the result slot
  once).
- Client config (`config/bedrockcraftingcontrols-client.toml`) with toggles:
  - `enableBedrockRecipeClickBehavior` (default `true`)
  - `normalClickLoadsRecipeOnly` (default `true`)
  - `shiftClickRecipeCraftsOne` (default `true`)
  - `shiftClickOutputCraftsMax` (default `true`)
  - `affectInventoryCrafting` (default `false`)
- Optional, off-by-default support for applying the behavior to the 2×2 inventory crafting grid.

### Notes
- Normal-click (load grid only) and shift-click-output (craft max) already match vanilla; those flags
  document the expectation and are reserved for forward-compatibility.
- No dependency on JEI or any other mod; no server-side installation required.
- Recipe-click behavior relies on vanilla container networking and still requires in-game testing
  (see the Testing checklist in `README.md`), including a multiplayer/desync pass.
