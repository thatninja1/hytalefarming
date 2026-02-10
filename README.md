# hytalefarming

Hytale plugin/mod `v1.0.1` that turns `Tool_Hoe_Thorium` into an upgradeable hoe.

## Interaction behavior
- Upgrade UI opens **only** on `Secondary` (right-click) while holding `Tool_Hoe_Thorium`.
- `Use` (F) with `Tool_Hoe_Thorium` now attempts crop harvest logic (not UI open).
- Non-supported interactions are ignored with debug reason logs.

## Commands
- `/tokens bal` (self)
- `/tokens bal --player <player>` (target)
- `/tokens pay --player <player> --amount <amount>`
- `/tokens give <player> <amount>`
- `/tokenstop`
- `/farming reload` (requires `hytalefarming.farming.reload`)

### `/farming reload`
Reloads plugin JSON configs from disk without restart:
- `tokens.json`
- `enchants.json`
- `config.json`

Behavior:
- Successfully parsed files are applied immediately.
- If a file fails parsing/loading, plugin keeps the last-known-good in-memory config for that file and logs the error.
- Reload always logs loaded files and whether defaults were applied for missing files.
- With debug enabled, caller, reloaded files, warnings, and errors are logged.

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
Each enchant uses:
- `maxLevel`
- `baseUpgradeCost`
- `enchantProc` (proc value from config)

Unified proc rule for all enchants (`token_finder`, `fortune`, `keyfinder`, future):
- If `enchantProc >= 1.0` => computed chance `1.0` (always proc)
- Else => `computedChance = enchantProc * (level / maxLevel)`
- Clamp to `[0, 1]`

Debug logs for valid harvests include:
- enchant id
- level
- maxLevel
- enchantProc (config)
- computedChance
- roll
- procResult

Keyfinder config keeps:

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

`{player}` -> player username, `<crateid>` -> selected crate id.

## Harvest behavior
- Valid crop detection remains:
  - crop id prefix (`Crop_` / `Plant_Crop_`)
  - fully-grown state (`State_Definitions_StageFinal`)
- `Use` (F) on valid fully-grown crop with thorium hoe performs a real server break and then enters the same shared crop-break proc/reward pipeline used by normal break events.
- `Use` on non-crop / non-final crop does nothing and does not open UI.
- Reward dedupe guard is applied per player+block position in a short window to avoid double-awards if Use + Break overlap.
- Debug logs include `source=PrimaryBreak` vs `source=UseHarvest`, cached held item id, cached broken block id, validation result, and per-enchant proc rolls/results.
- Harvest debug also reports real-break usage and vanilla-drop capture status (unknown when engine drop list is not exposed).

## Drop-to-inventory note
- In this plugin context, `BreakBlockEvent` does not expose computed drop lists for deterministic interception.
- Therefore vanilla block drops are preserved (ground drops), including vanilla Essence behavior (`Ingredient_Life_Essence`).
- Debug logs explicitly state when vanilla drops are left unchanged.
