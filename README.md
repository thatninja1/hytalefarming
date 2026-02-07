# hytalefarming

Hytale plugin/mod `v1.0.1` that turns `Tool_Hoe_Thorium` into an upgradeable hoe.

## Included
- Upgrade UI opens on right-click of `Tool_Hoe_Thorium`.
- Configurable token currency (`tokens.json`, name + multiplier).
- Persistent token balances per player.
- First enchant: `Token Finder` with configurable max level (`enchants.json`).
- Crop break token proc using formula: `tokensAwarded = level * tokensTimes`.
- Commands:
  - `/tokens bal` (self)
  - `/tokens bal --player <player>` (target)
  - `/tokens pay --player <player> --amount <amount>`
  - `/tokenstop`

## Notes
- This repo intentionally avoids committing binary wrapper artifacts to prevent PR tooling failures on binary files.
- Build uses plain Gradle + Hytale Maven dependency declarations.

- If your server setup does not automatically distribute asset packs, clients must also install the matching mod/assets version (v1.0.1) to avoid missing custom UI documents.
