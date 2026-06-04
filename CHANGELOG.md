# Changelog

All notable changes to Bedrock Crafting Controls are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - 2026-06-04

First public release for **Minecraft 1.21.1 / NeoForge 21.1.x**, client-only. Three Bedrock-style
crafting behaviors in the vanilla crafting table, each toggleable in config.

### Added
- **Shift-click a recipe → craft up to a stack** of the result into your inventory, instead of
  vanilla's "bulk-fill the grid." Count = `floor(resultMaxStackSize / yieldPerCraft)` (pickaxe → 1,
  torches → 64, stairs → 64), bounded by available materials.
- **Shift-click the output → craft the maximum** your inventory allows — re-fills the grid from your
  inventory and crafts the lot, instead of vanilla crafting only the sets already in the grid.
- **Normal-click the output → craft onto the cursor and keep the grid stocked**, so repeated clicks
  pile crafts onto the cursor up to a full stack — instead of vanilla emptying the grid after one
  craft. (Vanilla already accumulates onto a matching cursor; the mod just re-stocks the grid.)
- The two output behaviors identify the recipe from the **current grid contents** (client recipe
  manager), so they work for a hand-filled grid too, not just one loaded from the recipe book —
  matching Bedrock's inventory-backed grid. An incomplete grid → plain vanilla.
- Client config (`config/bedrockcraftingcontrols-client.toml`):
  - `enableBedrockRecipeClickBehavior` — master switch (default `true`)
  - `shiftClickRecipeCraftsStack` (default `true`)
  - `shiftClickOutputCraftsMax` (default `true`)
  - `clickOutputRefillsGrid` (default `true`)
  - `affectInventoryCrafting` — extend all three behaviors to the 2×2 inventory grid (default `false`)
  - `normalClickLoadsRecipeOnly` — documents the (unchanged) vanilla normal-click (default `true`)

### Implementation
- All behavior is a single client Mixin (`MultiPlayerGameModeMixin`) making only stock vanilla packet
  calls (`ServerboundPlaceRecipePacket`, `ServerboundContainerClickPacket`), so it works on vanilla
  servers with no server-side install and no dependency on JEI or any other mod.
- The yes/no decisions live in the pure, unit-tested `RecipeClickPolicy`; JUnit 5 tests run via the
  moddev `unitTest` harness, formatting is palantir-java-format via Spotless, and a GitHub Actions
  workflow builds + tests on every push/PR.
