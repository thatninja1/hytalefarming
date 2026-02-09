# hytalefarming

Hytale plugin/mod `v1.0.1` that turns `Tool_Hoe_Thorium` into an upgradeable hoe.

## Interaction behavior
- Upgrade UI opens **only** on `Secondary` (right-click) while holding `Tool_Hoe_Thorium`.
- `Use`/keybind interactions are ignored and logged in debug output.

## Config files

### `config.json`
UI text is editable by server owners:

```json
{
  "ui": {
    "title": "Ninja Farming",
    "subtitle": "Upgrade your Thorium Hoe"
  }
}
```

### `enchants.json`
Each enchant supports:
- `maxLevel`
- `baseUpgradeCost`
- `enchantProc` (proc chance at **max level**)

Proc scaling rule:
- if `enchantProc == 1.0`: always proc at every level
- else: `effectiveProc = enchantProc * (level / maxLevel)`

The plugin logs for each enchant roll:
- enchant name
- level
- maxLevel
- enchantProc
- effectiveProc
- random roll and result

Default structure:

```json
{
  "tokenFinder": {
    "maxLevel": 10,
    "baseUpgradeCost": 10,
    "enchantProc": 1.0
  },
  "fortune": {
    "maxLevel": 5,
    "baseUpgradeCost": 20,
    "upgradeCostIncrease": 100,
    "enchantProc": 1.0
  },
  "keyfinder": {
    "maxLevel": 100,
    "baseUpgradeCost": 50,
    "enchantProc": 1.0,
    "crates": [
      {
        "crateId": "Crate1",
        "command": "/crates givekey {player} <crateid>",
        "crate_chance": 0.5
      },
      {
        "crateId": "Crate2",
        "command": "/crates givekey {player} <crateid>",
        "crate_chance": 0.5
      }
    ]
  }
}
```

## Enchants

### Token Finder
- On valid fully-grown crop break, may award tokens based on proc roll.

### Fortune
- Defaults: `maxLevel=5`, `baseUpgradeCost=20`, `upgradeCostIncrease=100`, `enchantProc=1.0`.
- Upgrade costs default to: `20, 120, 220, 320, 420`.
- On proc: extra crop items from the broken crop type.
  - L1: `random(0,1)`
  - L2: `random(1,2)`
  - LN: `random(N-1,N)`
- Example block `Plant_Crop_Carrot_Block_State_Definitions_StageFinal` grants `Plant_Crop_Carrot_Item` extras.

### Keyfinder
- Defaults: `maxLevel=100`, `baseUpgradeCost=50`, `enchantProc=1.0`.
- Level 1 effective proc is ~1% with default scaling.
- After proc succeeds, crate selection uses weighted `crateChance` across configured crates.
- Selected crate command is executed after replacements:
  - `{player}` -> player username
  - `<crateid>` -> selected crate id

## Existing commands
- `/tokens bal` (self)
- `/tokens bal --player <player>` (target)
- `/tokens pay --player <player> --amount <amount>`
- `/tokens give <player> <amount>` (online target, requires `hytalefarming.tokens.give`)
- `/tokenstop`

## Notes
- Proc effects only run for fully-grown crops (`State_Definitions_StageFinal`).
