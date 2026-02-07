# StarhavenSMP Core
![Files](https://img.shields.io/badge/dynamic/json?style=for-the-badge&cacheSeconds=300&label=Files&url=https%3A%2F%2Fapi.codetabs.com%2Fv1%2Floc%3Fgithub%3DExoHayvan%2FStarhavenSMP-Core&query=%24%5B5%5D.files)
![LoC](https://img.shields.io/badge/dynamic/json?style=for-the-badge&cacheSeconds=300&label=Lines&url=https%3A%2F%2Fapi.codetabs.com%2Fv1%2Floc%3Fgithub%3DExoHayvan%2FStarhavenSMP-Core&query=%24%5B5%5D.linesOfCode)
![GitHub repo size](https://img.shields.io/github/repo-size/ExoHayvan/StarhavenSMP-Core?style=for-the-badge)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/ExoHayvan/StarhavenSMP-Core/total?style=for-the-badge)
![Codacy grade](https://img.shields.io/codacy/grade/faa450001a6643fdbe37a4488a4414bc?style=for-the-badge)

StarhavenSMP Core is the main SMP plugin for Starhaven. It includes the dynamic market system, auto-sell, web analytics, resource pack delivery, custom items/blocks, and custom ore generation.

## Features

- Market GUI with dynamic pricing based on supply/demand and economy activity
- Auto-sell GUI tied to a per-player list
- Web dashboard (port 6969) for market listings and sellers
- PlaceholderAPI support for global and player stats
- Resource pack auto-delivery from GitHub releases (SHA1 enforced)
- Custom items + custom blocks using note_block states

## Commands

| Command | Description |
|---|---|
| `/market` | Open the market GUI. |
| `/market buy <filter>` | Open buy listings with optional filter. |
| `/market sell` | Open sell GUI. |
| `/market items <filter>` | Open your listings with optional filter. |
| `/market info` | Show market summary. |
| `/sellhand` | Sell the item in your main hand. |
| `/marketreload` | Reload config + resource pack settings. |
| `/autosell` | Open the auto-sell GUI. |
| `/starhavengive <player> <starhaven:item> [amount]` | Give custom items. |

## Permissions

| Permission | Description |
|---|---|
| `starhaven.items.give` | Allows `/starhavengive` and `/give <player> starhaven:item`. |

## Custom Items / Blocks

Registered custom items:
- `starhaven:void_shard`
- `starhaven:voidstone_ore`
- `starhaven:void_block`

Behavior highlights:
- **Void Block** drops itself.
- **Voidstone Ore** requires **iron+ pickaxe** to drop.
- **Voidstone Ore** drops **Void Shard** with low fortune bonus.
- **Silk Touch** drops the ore block and **no XP**.
- **XP**: 12–19 per ore when not silk-touch.
- Custom blocks are note_block-backed and protected from burn/ignite.

## Ore Generation

The ore generation system runs when players load chunks that were not previously processed.

Voidstone Ore rules:
- Only spawns in **The End**
- 1 vein per chunk
- 1–2 blocks per vein
- Only replaces **endstone**
- 5% chance to spawn on top-facing endstone; otherwise buried

Database: `plugins/StarhavenSMPCore/ore_generation.db`

## PlaceholderAPI Placeholders

### Global stats
| Placeholder | Description |
|---|---|
| `%exomarket_total_items_bought%` | Total items bought globally |
| `%exomarket_total_items_sold%` | Total items sold globally |
| `%exomarket_total_money_spent%` | Total money spent globally |
| `%exomarket_total_money_earned%` | Total money earned globally |
| `%exomarket_total_listed_items%` | Total items currently listed |

### Player stats
| Placeholder | Description |
|---|---|
| `%exomarket_player_items_bought%` | Items bought by player |
| `%exomarket_player_items_sold%` | Items sold by player |
| `%exomarket_player_money_spent%` | Money spent by player |
| `%exomarket_player_money_earned%` | Money earned by player |
| `%exomarket_player_listed_items%` | Player’s listed items |

## Resource Pack Delivery

Configurable GitHub release downloader that:
- polls the latest release
- selects a ZIP asset by prefix/name
- serves a SHA1 hash to clients

Relevant config keys are under `ResourcePack` in `config.yml`.

## Configuration (config.yml)

```yaml
MarketManager:
  MarketValueMultipier: 6
  MaxPricePercent: 5
  MinPrice: 0.01

CurrencySymbol: "⚚Ɍ"

ResourcePack:
  Enabled: false
  GitHubOwner: ""
  GitHubRepo: ""
  AssetPrefix: "StarhavenSMP-ResourcePack-"
  AssetName: ""
  Prompt: "This server requires the StarhavenSMP resource pack."
  Force: true
  RefreshSeconds: 3600
  GitHubToken: ""
```

## Databases

Stored in `plugins/StarhavenSMPCore/`:
- `database.db` — market listings + stats
- `autosell.db` — per-player auto-sell items
- `ore_generation.db` — chunk generation markers

## Build

```bash
mvn clean package
```

## Dependencies

- Spigot API 1.21
- EssentialsX
- PlaceholderAPI
- Vault

## Notes

- Web UI runs on port **6969**.
- Custom blocks are note_block-backed and rely on the resource pack mappings.
