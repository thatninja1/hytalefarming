# hytalefarming

Hytale plugin/mod `v2.0` for Farming+ sickles with upgrade UI, token currency, and enchant procs.

## Supported farming tools
The plugin treats these item IDs as valid farming tools:
- `Tool_Sickle_Adamantite`
- `Tool_Sickle_Cobalt`
- `Tool_Sickle_Crude`
- `Tool_Sickle_Gold`
- `Tool_Sickle_Iron`
- `Tool_Sickle_Mithril`
- `Tool_Sickle_Steel_Rusty`
- `Tool_Sickle_Thorium`

## Interaction behavior
- `Secondary`, `Primary`, and `Use` with supported sickles are treated as harvest/proc interactions only.
- UI no longer opens from sickle interactions. Use `/farming upgrade` to open the upgrade UI.
- Primary/Secondary with a supported sickle cache short-lived context so subsequent engine AOE `BreakBlockEvent`s can still be attributed when event hand-item metadata is null.

## Commands
- `/tokens bal` (self)
- `/tokens bal --player <player>` (target)
- `/tokens pay --player <player> --amount <amount>`
- `/tokens give <player> <amount>`
- `/tokenstop`
- `/farming reload` (requires `hytalefarming.farming.reload`)
- `/farming upgrade`

## Config files

### `config.json`
```json
{
  "debug": true,
  "ui": {
    "title": "Ninja Farming",
    "subtitle": "Upgrade your Farming Tool"
  }
}
```

Debug behavior:
- Debug traces (`[FarmingDebug]`, `[Harvest]`, `[CropBreak]`, `[EnchantProc]`, `[Drops]`, etc.) are gated by debug.
- If debug is false, debug traces are suppressed.
- Warnings/errors still log.

### `enchants.json`
Each enchant supports:
- `maxLevel`
- `baseUpgradeCost`
- `enchantProc`
- `procMessage`
- `upgradeCostIncrease` (for enchants that use linear increasing costs)

`tokenFinder` also supports:
- `defaultLevel`

#### `procMessage` placeholders
- `{amount}` token amount from Token Finder
- `{currency}` configured currency name
- `{enchant}` enchant display name
- `{level}` enchant level
- `{crateId}` chosen keyfinder crate id
- `{extra}` fortune extra crop amount
- `{count}` Eternal Growth advanced-crop count

#### Hex color support
- `#RRGGBB` tokens are accepted in templates.
- If hex chat colors are unsupported by runtime, hex tokens are stripped.
- Startup logs a once-only warning: `Hex chat colors not supported; stripping codes.`

#### Token Finder default level
- New players get `token_finder = tokenFinder.defaultLevel`.
- Existing players keep saved values.
- Existing players missing `token_finder` get migrated once.

## Proc and crop rules
- Proc logic runs only for fully-grown crop blocks (`State_Definitions_StageFinal`).
- Radius harvesting procs are per-block (independent rolls per crop block).
- On Primary/Secondary sickle input, plugin snapshots the sickle area, verifies over multiple passes (~100ms, ~200ms, ~350ms), and invokes proc pipeline for positions that changed away from fully-grown state.
- Primary/Secondary AOE proc chat is aggregated into a single summary message per swing (rewards still apply per crop).
- Debug includes snapshot counts (`snapshotCount`, `fullyGrownCount`, `harvestedDetectedCount`, `procInvocationCount`) and radius summary windows (`processed=X fullyGrown=Y procs=Z`).
- In mixed-radius harvests, only fully-grown crops are eligible.

## Drop handling
- Plugin attempts to intercept vanilla break drops and add them to player inventory, including `Ingredient_Life_Essence`.
- If the engine/API does not expose deterministic drop data, plugin falls back to vanilla ground drops and logs fallback/debug details.

## Keyfinder
- Key commands support `{player}` and `<crateid>` replacement in configured command strings.

## Eternal Growth
- Enchant key: `eternal_growth` (display: `Eternal Growth`).
- Applies only to eternal crops (`_Block_Eternal_State_Definitions_...`).
- On a successful proc, harvested eternal crops that reset to `_Stage1` are immediately advanced to `_Stage2`.
- Works for `Use` harvest and Primary/Secondary AOE harvest processing.
- In AOE batch mode, Eternal Growth messages are aggregated into the single swing summary using `{count}`.
