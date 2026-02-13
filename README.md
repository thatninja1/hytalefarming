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
UI text and debug toggle are editable:

```json
{
  "debug": true,
  "ui": {
    "title": "Ninja Farming",
    "subtitle": "Upgrade your Thorium Hoe"
  }
}
```

`debug` behavior:
- All plugin debug traces (`[HoeDebug]`, `[Harvest]`, `[CropBreak]`, `[EnchantProc]`, `[Drops]`, etc.) are gated by the debug flag.
- If `debug=false`, those debug traces are suppressed.
- Warnings/errors still log.

### `enchants.json`
Each enchant uses:
- `maxLevel`
- `baseUpgradeCost`
- `enchantProc` (proc value from config)
- `procMessage` (chat message template used when that enchant procs)

`tokenFinder` also supports:
- `defaultLevel` (applies only when player has no saved token finder level yet)

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

Example:

```json
{
  "tokenFinder": {
    "maxLevel": 10,
    "baseUpgradeCost": 10,
    "defaultLevel": 1,
    "enchantProc": 1.0,
    "procMessage": "#808080+{amount} {currency} (#FFD700{enchant}#808080)"
  },
  "fortune": {
    "maxLevel": 5,
    "baseUpgradeCost": 20,
    "upgradeCostIncrease": 100,
    "enchantProc": 1.0,
    "procMessage": "#80FF80Fortune proc! +{extra} crops"
  },
  "keyfinder": {
    "maxLevel": 100,
    "baseUpgradeCost": 50,
    "enchantProc": 1.0,
    "procMessage": "#00FFFFKeyfinder! You found a key: {crateId}",
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

`{player}` -> player username, `<crateid>` -> selected crate id in the configured command.

`procMessage` placeholders:
- `{amount}`: token amount awarded (Token Finder)
- `{currency}`: configured currency name
- `{enchant}`: enchant display name (Token Finder / Fortune / Keyfinder)
- `{level}`: current enchant level
- `{crateId}`: selected keyfinder crate id
- `{extra}`: extra crop amount from Fortune

### Hex colors in proc messages
- Hex markers in `#RRGGBB` format are accepted in templates.
- Current server chat path does **not** support hex formatting directly, so hex markers are stripped before sending.
- Plugin logs this warning once on startup: `Hex chat colors not supported; stripping codes.`

### Token Finder default level behavior
- New players are initialized with `token_finder = tokenFinder.defaultLevel`.
- Existing players keep their stored level.
- Migration case is supported: if a player exists in data but `token_finder` key is missing, the default is applied once.

## Harvest behavior
- Valid crop detection remains:
  - crop id prefix (`Crop_` / `Plant_Crop_`)
  - fully-grown state (`State_Definitions_StageFinal`)
- `Use` (F) on a valid fully-grown crop with thorium hoe now **does not force-break the block in plugin code**; instead it registers pending context, waits briefly, verifies vanilla harvest changed the block away from `StageFinal`, then invokes the same shared proc/reward pipeline used by normal break events.
- `Use` on non-crop / non-final crop does nothing and does not open UI.
- Reward dedupe guard is applied per player+block position in a short window to avoid double-awards if Use + Break overlap.
- Debug logs include `source=PrimaryBreak` vs `source=UseHarvest`, cached held item id, cached broken block id, validation result, and per-enchant proc rolls/results.
- Harvest debug reports: pending-context registration, delayed verification scheduling, block state before/after, whether vanilla harvest was observed, and whether proc pipeline was invoked or skipped.

## Drop-to-inventory note
- In this plugin context, `BreakBlockEvent` does not expose computed drop lists for deterministic interception.
- Therefore vanilla block drops are preserved (ground drops), including vanilla Essence behavior (`Ingredient_Life_Essence`).
- Debug logs explicitly state when vanilla drops are left unchanged.
