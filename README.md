# hytalefarming

Hytale plugin/mod `v1.0.1` that turns `Tool_Hoe_Thorium` into an upgradeable hoe.

## Interaction behavior
- Upgrade UI opens **only** on `Secondary` (right-click) while holding `Tool_Hoe_Thorium`.
- Non-Secondary interactions (including `Use`/F key) are ignored.
- Debug logs show accepted vs ignored interaction types.

## Config files

### `config.json`
UI text is editable:

```json
{
  "ui": {
    "title": "Ninja Farming",
    "subtitle": "Upgrade your Thorium Hoe"
  }
}
```

### `enchants.json`
Each enchant uses the existing schema with:
- `maxLevel`
- `baseUpgradeCost`
- `enchantProc` (proc value from config, 0..1+)

Unified proc rule for **all** enchants (`token_finder`, `fortune`, `keyfinder`, future):

- If `enchantProc >= 1.0` => computed proc chance is `1.0` (always proc at any level)
- Else => `computedChance = enchantProc * (level / maxLevel)`
- Final chance is clamped to `[0, 1]`

Debug logs on valid fully-grown crop breaks include:
- enchant id
- level
- maxLevel
- enchantProc from config
- computedChance
- roll
- procResult

Keyfinder crate config keeps the current schema:

```json
{
  "keyfinder": {
    "maxLevel": 100,
    "baseUpgradeCost": 50,
    "enchantProc": 1.0,
    "crates": [
      {
        "crateId": "Crate1",
        "command": "/crates givekey {player} <crateid>",
        "crate_chance": 0.5
      }
    ]
  }
}
```

`{player}` is replaced with the player username. `<crateid>` is replaced with selected `crateId`.

## Crop break behavior
- Enchant logic runs only for fully-grown crops (`State_Definitions_StageFinal`) broken with `Tool_Hoe_Thorium`.
- Crop block id is still mapped to crop item id correctly (example Carrot block => `Plant_Crop_Carrot_Item`).
- Fortune extra crops still use `random(level-1, level)`.

## Drop-to-inventory note
- Current `BreakBlockEvent` API in this plugin context does **not** expose a computed drop list to intercept.
- Because of that, vanilla drops are currently left unchanged (ground drops).
- Debug log records: `Could not intercept drop list; leaving vanilla drops`.
- This means exact vanilla crop/Essence (`Ingredient_Life_Essence`) amounts are preserved by vanilla behavior, but not moved directly into inventory by this plugin at this time.

## Keyfinder awarding
- Crate command runs only if Keyfinder proc succeeds.
- Exactly one crate command is selected per proc via weighted `crate_chance`.
- Invalid crate config (no crates / total weight <= 0) logs warning and executes nothing.

## Existing commands
- `/tokens bal` (self)
- `/tokens bal --player <player>` (target)
- `/tokens pay --player <player> --amount <amount>`
- `/tokens give <player> <amount>`
- `/tokenstop`
