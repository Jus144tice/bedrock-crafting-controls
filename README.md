# Bedrock Crafting Controls

A small, **client-side** quality-of-life mod that recreates Bedrock-style crafting controls in the
vanilla crafting table.

## Requires

- **Minecraft: Java Edition 1.21.1**
- **NeoForge** (21.1.x, for 1.21.1)
- **Java 21** — bundled by the Minecraft launcher; only needed on its own if you build from source

> **Client-only.** Install it on your client only — it does **not** need to be on the server, works on
> vanilla servers, and runs fine alongside other client mods.

---

## What it does

In the vanilla **crafting table** recipe book:

| Action | Vanilla Java | With this mod |
| --- | --- | --- |
| **Normal-click** a recipe | Loads one set of ingredients into the grid (no craft) | **Same** — loads the grid only, no craft |
| **Shift-click** a recipe | Fills the grid with as many sets as possible | **Crafts up to a stack** of the result into your inventory |
| **Shift-click** the output slot | Crafts only the sets already sitting in the grid | **Crafts as many as your inventory allows** (re-fills the grid, then crafts it all) — matching Bedrock |
| **Normal-click** the output slot | Crafts one set onto your cursor; the grid empties | **Crafts onto your cursor and leaves the ingredients on the table** — keep clicking to pile up to a stack on the cursor, matching Bedrock |

So this mod changes **three** interactions, all to match Bedrock:

1. **Shift-click a recipe** → instead of stuffing the grid full, craft up to **one stack of the
   result** into your inventory: a stone pickaxe makes 1, torches make a full 64, stairs make 64
   (consuming more than a stack of stone). Bounded by the materials you have.
2. **Shift-click the output** → instead of crafting only the one-or-few sets currently in the grid,
   pull all the matching materials from your inventory and craft the maximum.
3. **Normal-click the output** → instead of vanilla emptying the grid after one craft, the ingredients
   stay on the table (re-stocked from your inventory) so you can keep clicking to add more crafts to
   the stack on your cursor, up to a full stack.

Normal-click on a *recipe* is unchanged (vanilla already just loads the grid).

### How it works (the honest version)

Everything goes through the **normal vanilla networking path** (`ServerboundPlaceRecipePacket` +
`ServerboundContainerClickPacket`), which is why no server-side installation is required.

**Shift-click a recipe → craft a stack:**

The mod figures out how many crafts make one stack of the result —
`floor(resultMaxStackSize / yieldPerCraft)` (so 4-per-craft torches → 16 crafts → 64 torches; a
1-stack pickaxe → 1 craft) — then repeats, that many times:

1. Places **one** set of ingredients into the grid (the normal, non-bulk placement), then
2. Shift-clicks the **result slot once** (crafts that one set's output).

Once your inventory runs out of materials the remaining repeats are harmless no-ops, so you always get
"up to a stack, or as many as you could afford."

The two **output**-slot features below figure out which recipe is in the grid by looking it up from
the grid contents — so they work whether you auto-filled it from the recipe book **or placed the
ingredients by hand**, matching Bedrock (whose grid is backed by your inventory). If the grid doesn't
currently form a complete recipe, they fall back to plain vanilla.

**Shift-click the output → craft max:**

1. Re-fills the grid with as many sets as your inventory allows (the vanilla "place all" placement),
   using the recipe currently in the grid, then
2. Shift-clicks the **result slot once** — vanilla's quick-move loops until the grid is empty, so it
   crafts every set just placed.

**Normal-click the output → craft to cursor, repeatedly:**

1. Lets vanilla do the normal click — it crafts one set onto your cursor (and, when your cursor
   already holds the same item, tops it up — that part is already vanilla), then
2. Re-places **one** set of ingredients into the grid from your inventory.

Because the grid is re-stocked, the next click crafts again and adds to your cursor. Keep clicking to
build up to a full stack; it stops when the cursor is full or your materials run out.

---

## Config

A config file is generated at `config/bedrockcraftingcontrols-client.toml` on first run:

```toml
[crafting]
    # Master switch. When false the mod does nothing and vanilla behavior is used.
    enableBedrockRecipeClickBehavior = true

    # Normal-click loads the grid only (vanilla already does this; documentation/forward-compat).
    normalClickLoadsRecipeOnly = true

    # Shift-click a recipe crafts up to a stack of the result into your inventory (the core feature).
    shiftClickRecipeCraftsStack = true

    # Shift-click the output crafts as many as the inventory allows (Bedrock-style; the mod hooks this).
    shiftClickOutputCraftsMax = true

    # Normal-click the output re-stocks the grid so you can keep clicking to pile a stack onto your
    # cursor (Bedrock-style; the mod hooks this).
    clickOutputRefillsGrid = true

    # Also apply all three behaviors to the 2x2 inventory grid. Off by default.
    affectInventoryCrafting = false
```

Notes:

* `enableBedrockRecipeClickBehavior`, `shiftClickRecipeCraftsStack`, `shiftClickOutputCraftsMax`, and
  `clickOutputRefillsGrid` are the flags with real teeth (the master switch and the three Bedrock
  behaviors).
* `normalClickLoadsRecipeOnly` describes behavior vanilla already provides; it exists for clarity and
  forward-compatibility and does not add a hook.
* `affectInventoryCrafting` extends **all three** Bedrock behaviors to the 2×2 grid in your inventory.
  Left disabled by default as requested.

---

## Compatibility

* **No dependency on JEI**, AppleSkin, Mouse Tweaks, or Inventory Profiles Next. None of their
  classes are referenced or modified.
* Does not modify JEI recipe display, server-side recipes, or use datapacks.
* The mod hooks `MultiPlayerGameMode#handlePlaceRecipe` (recipe-book clicks) and
  `#handleInventoryMouseClick` (the output shift-click). Both only act when the open menu is a
  `CraftingMenu` (3×3 table) — or `InventoryMenu` (2×2) if you opt in — so furnaces, smithing tables,
  stonecutters, etc. are untouched.
* Inventory-management mods (Mouse Tweaks / Inventory Profiles Next) operate on slot clicks and
  sorting; this mod only intercepts the *recipe-book* placement call, so conflicts are unlikely.
  See the **Testing checklist** below to confirm in your pack.

---

## Building

Requires a **JDK 21** (NeoForge 1.21.1 runs on Java 21). The Gradle wrapper handles Gradle itself.

```bash
# from the project root
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

If your `JAVA_HOME` does not point at a JDK 21, either set it, or rely on Gradle's toolchain
auto-detection / download (the `foojay-resolver` plugin is configured to provision one).

The built jar is written to:

```
build/libs/bedrockcraftingcontrols-1.0.0.jar
```

(`bedrockcraftingcontrols` is the `archivesName`; the version comes from `gradle.properties`.)

### Running in a dev client

```bash
./gradlew runClient
```

---

## Installing into a Modrinth NeoForge profile

1. Build the jar (above) or grab a released `bedrockcraftingcontrols-<version>.jar`.
2. In the **Modrinth App**, open (or create) a **NeoForge 1.21.1** profile.
3. Open the profile's content / **Add content → From file** (or open the profile folder and drop the
   jar into its `mods/` directory).
   * The profile folder is reachable from the Modrinth App via the profile's **⋯ → Open folder**;
     the jar goes in `<profile>/mods/`.
4. Launch the profile. Because the mod is client-only, you can join vanilla or modded servers with
   it installed.

---

## Testing checklist

Handy checks when validating a release or bringing the mod to a new Minecraft / NeoForge version
(the recipe-click behavior relies on vanilla container networking, so it's worth a quick in-game pass):

- [ ] Singleplayer crafting table: **normal-click** a recipe → grid is populated with one set, output
      shows a preview, nothing is crafted into inventory yet.
- [ ] Singleplayer crafting table: **shift-click** a recipe with plenty of ingredients → up to **one
      stack of the result** lands in your inventory (a pickaxe → 1; torches → 64; stairs → 64,
      consuming more than a stack of stone), and the right amount of ingredients is consumed.
- [ ] Shift-click a recipe with **fewer materials than a stack needs** → crafts as many as the
      materials allow and then stops; no item duplication, no stuck cursor.
- [ ] Shift-click a recipe with **not enough for even one** → behaves sanely (ghost recipe / nothing
      crafted), no duplication.
- [ ] Load a recipe (normal-click), then **shift-click the output slot** → the grid re-fills from
      your inventory and you craft the **maximum** (one click empties the matching materials), not
      just the single set that was loaded.
- [ ] Fill the grid **by hand** (not via the book), then shift-click / normal-click the output → the
      mod still kicks in (resolves the recipe from the grid): shift-click crafts the max, normal-click
      keeps re-stocking. No duplication.
- [ ] Put an **incomplete / non-recipe** arrangement in the grid → output clicks fall back to plain
      vanilla (no result, no placement).
- [ ] Load a recipe (normal-click), then **normal-click the output repeatedly** → each click adds a
      craft to the stack on your cursor and the ingredients stay on the table; it stops at a full
      cursor stack or when materials run out. No duplication, correct counts after the server resyncs.
- [ ] Set `clickOutputRefillsGrid = false` → normal-click output reverts to vanilla (one craft, grid
      empties).
- [ ] Set `shiftClickOutputCraftsMax = false` → output shift-click reverts to vanilla (crafts only the
      grid contents).
- [ ] Set `enableBedrockRecipeClickBehavior = false` → all three behaviors revert fully to vanilla.
- [ ] Set `affectInventoryCrafting = true` → the 2×2 inventory grid gets all three behaviors;
      with it `false`, the 2×2 grid is vanilla.
- [ ] **Multiplayer / dedicated vanilla server**: repeat the shift-click-recipe test and watch for
      desync (item should not flicker back, no ghost items, counts correct after the server resyncs).
- [ ] With **JEI, AppleSkin, Mouse Tweaks, Inventory Profiles Next** installed: open the crafting
      table, sort/move items, and confirm no crashes and that recipe shift-click still crafts a stack.
- [ ] Confirm **furnace / smithing / stonecutter / anvil** screens are unaffected.

---

## License

[Apache License 2.0](LICENSE).
