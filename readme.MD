# StarhavenSMP Core

![SERVER](https://img.shields.io/website?url=http%3A%2F%2Fstarhavensmp.mcserver.us%3A8000&up_message=Online&down_message=Offline&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA5OTUuMyA5OTUuMzEiPjxkZWZzPjxjbGlwUGF0aCBpZD0iYSI+PHBhdGggZmlsbD0ibm9uZSIgZD0iTS0uMDA1IDQ5OC43MjggNDk2LjYwNy4wMzJsNDk4LjY5NSA0OTYuNjEyLTQ5Ni42MSA0OTguNjk2eiIvPjwvY2xpcFBhdGg+PGNsaXBQYXRoIGlkPSJiIj48cGF0aCBmaWxsPSJub25lIiBkPSJNNy43MDIgNDEwLjQ0NiA1ODQuODQ2IDcuNjc0IDk4Ny42MTggNTg0LjgyIDQxMC40NzMgOTg3LjU5eiIvPjwvY2xpcFBhdGg+PGNsaXBQYXRoIGlkPSJjIj48cGF0aCBmaWxsPSJub25lIiBkPSJtMjguMzUzIDMzMi4wNDcgNjM0Ljg2LTMwMy43NjUgMzAzLjc2NSA2MzQuODYtNjM0Ljg2IDMwMy43NjV6Ii8+PC9jbGlwUGF0aD48Y2xpcFBhdGggaWQ9ImQiPjxwYXRoIGZpbGw9Im5vbmUiIGQ9Im03MS41NTkgMjQwLjU0IDY4My4yMS0xNjguOTUyIDE2OC45NSA2ODMuMjFMMjQwLjUxIDkyMy43NXoiLz48L2NsaXBQYXRoPjwvZGVmcz48cGF0aCBmaWxsPSIjZjdlMzQ2IiBkPSJNLjAwNSA0OTguNzI4IDQ5Ni42MTcuMDMybDQ5OC42OTUgNDk2LjYxMi00OTYuNjEgNDk4LjY5NnoiLz48ZyBjbGlwLXBhdGg9InVybCgjYSkiPjxwYXRoIGZpbGw9IiNhNDk3MzEiIGQ9Ik0zLjUyNSA0MzguNDIgNTU2LjgzMSAzLjQ4bDQzNC45NCA1NTMuMzA3LTU1My4zMDYgNDM0Ljk0eiIvPjwvZz48cGF0aCBmaWxsPSIjNDI4ZWRiIiBkPSJNNy42OTggNDEwLjQ1NCA1ODQuODQyIDcuNjgzbDQwMi43NzEgNTc3LjE0NEw0MTAuNDcgOTg3LjU5OHoiLz48ZyBjbGlwLXBhdGg9InVybCgjYikiPjxwYXRoIGZpbGw9IiMyYjVmOTIiIGQ9Ik0xOC45OTkgMzYxLjQ2NCA2MzMuODMyIDE4Ljk3bDM0Mi40OTIgNjE0LjgzMy02MTQuODMyIDM0Mi40OTJ6Ii8+PC9nPjxwYXRoIGZpbGw9IiM5NmM5M2QiIGQ9Im0yOC4zNjYgMzMyLjA1MiA2MzQuODYtMzAzLjc2NSAzMDMuNzY1IDYzNC44Ni02MzQuODYgMzAzLjc2NXoiLz48ZyBjbGlwLXBhdGg9InVybCgjYykiPjxwYXRoIGZpbGw9IiM2Yzk4M2YiIGQ9Ik01OC43NTcgMjYzLjA2OCA3MzIuMjYgNTguODI3IDkzNi41IDczMi4zMyAyNjIuOTk5IDkzNi41N3oiLz48L2c+PHBhdGggZmlsbD0iI2ZkNGY1NyIgZD0ibTcxLjU2NiAyNDAuNTM3IDY4My4yMS0xNjguOTUgMTY4Ljk1MSA2ODMuMjEtNjgzLjIxIDE2OC45NXoiLz48ZyBjbGlwLXBhdGg9InVybCgjZCkiPjxwYXRoIGZpbGw9IiNiMDM2M2MiIGQ9Im0xMjkuNzgxIDE2Mi40OTIgNzAzLjAyNi0zMi43ODUgMzIuNzg1IDcwMy4wMjYtNzAzLjAyNiAzMi43ODV6Ii8+PC9nPjxwYXRoIGZpbGw9IiMzZDNkM2QiIGQ9Ik0xNDUuNzYgMTQ1Ljc2aDcwMy43OXY3MDMuNzlIMTQ1Ljc2eiIvPjxwYXRoIGZpbGw9IiM0NzQ3NDciIGQ9Ik0xNjQuNjEgMTY0LjZIODMwLjd2NjY2LjA5SDE2NC42MXoiLz48ZyBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxwYXRoIGZpbGw9IiMzMzMiIGQ9Ik01NTQuNDUgNDYyLjMyYy01NC4zOSA0Mi4xMy02OS4yMyA1Ny4yMi0xNjQuMzUgMTI3LjZsLTkuNjItNC44NGM1MS42LTQxLjcgMTI3LjU4LTEwMi42MiAxNzkuMTQtMTQ0LjI2IDE5LjI1LTE1LjI4IDM4LjY5LTM5LjExIDU3LjctNTQuOTF6bS0yMS43OCAxODguNzZjMy40NiAxLjcyIDYuMjUgMi42IDkuNjIgNC4zLTI2LjAzIDIzLjEyLTUzLjc2IDQ5LjI1LTc4Ljk2IDczLjYxLTQuODkgMi4zLTkuMzMgMS43OS0xMy4zMi0xLjU0bDE4LjQ1LTE5LjQ3YTQzMzAgNDMzMCAwIDAgMCA1Ny4zOS01NC44MmMzLjItMi40IDMuMTItMy43MiA2LjgzLTIuMDhabTExMy42NSA0Mi41NWMzLjcyLTIuMzEgNS42Ni00LjEyIDcuMDctNy42NSAxOS44Ny0xMjYuNjIgNDAuOTktMjUyLjk2IDYzLjM1LTM3OS4wNiA1LjA5LTI1LjMyIDYuMDgtMjEuNzYgNi4zNC0zMy43OSAxMS40MyAxLjc3IDE2LjE4IDguNDYgMTQuMjUgMjAuMDYtNS4zIDMyLjUzLTExLjI5IDY0LjkxLTE3Ljk1IDk3LjE0LTE1LjQxIDk0Ljc1LTMwLjk5IDE4OS40Ni00Ny4yNiAyODQuMDYtMS4zNyAxMC40NC0yLjE1IDE3LjIxLTQuNDcgMjEuMDktMy42OCA0LjUyLTguNDMgNi4xLTE0LjI1IDQuNzUtNC44My0xLjMzLTMuNzgtMS4xLTkuNjgtMy4zNGwtMy4yMS0xLjMzczMuOTktLjk1IDUuODItMS45M1oiLz48cGF0aCBmaWxsPSIjOTk5IiBkPSJNNTMyLjY3IDY1MS4wOWMtMS4yMy0uMTQtMi40OC0uMzUtMy41OS40NWEyMDA5IDIwMDkgMCAwIDAtNjIuOSA2MC42NWMtLjEzLS45NS4wMi0xLjg0LjQ1LTIuNyAzLjI5LTQuNzkgNS44OS04LjU3IDkuMTgtMTMuMzYuNjUtMS42My41NC0zLjA3LjQ0LTQuMDktMi4zOC0yNS41NS0xMC4wNS00OS40Ni0yMS43NS03Mi40MS0uMjUtLjQ4LjI1LTEuMjEgMS4zNS0uOSAyNS44NyAxMC4zNiA1MS40OCAyMS4xNSA3Ni44MiAzMi4zNVoiLz48cGF0aCBmaWxsPSIjY2NjIiBkPSJNNTU5LjYzIDQ0MC44M2E4NTM3IDg1MzcgMCAwIDEtMTgxLjk1IDE0Ni40NmMtMzYuNS0xNC44NC03Mi45Ni0yOS44LTEwOS4zNy00NC44Ny0xNC4wOC01LjgzLTE0LjAzLTE4LjU0LTEuNi0yNy4wMS02Ljc0IDguNTQtNS41NCAxNS44OCAzLjU5IDIyLjAxIDEyLjQ5IDUuMyAxMDYuNDEgNDQuMjUgMTA2LjQxIDQ0LjI1cy0uNzYtMy4wMyAxODIuOTEtMTQwLjg0WiIvPjxwYXRoIGZpbGw9IiNiMmIyYjIiIGQ9Ik02MzYuOSA2ODcuOTNjMTMuOTQgNC45OCAxMy40MyAxLjUgMTkuMzktNC4wOC0uNTEgMy45Ni0uOTUgOC4zLTUuOTkgMTEuMTItMy40NiAyLTYuNTkgMS45My05LjgxLjZhMTgyMCAxODIwIDAgMCAxLTEwNy44Mi00NC40OGMtMjUuMzQtMTEuMi01MC45NS0yMS45OC03Ni44Mi0zMi4zNWwtMS4zNS45YzEyLjY0IDIzLjYyIDE5Ljk4IDQ4Ljc3IDIyLjAxIDc1LjQ4LTMuMjkgNC43OS02LjU5IDkuNTgtOS44OCAxNC4zOHEtLjY0NSAxLjI3NS0uNDUgMi43bC0xNi4xNyAxNS4yN2MtNC4wNi0uMDEtNi4zMS0yLjExLTYuNzQtNi4yOS0uNTktMzMuNzMtLjYtNjcuNDYtLjAyLTEwMS4xOSAwLS40MS4wMS0uODEuMDItMS4yNC4xNS01LjA3IDUuMjYtNi41MiAxMC4zMy00Ljk0cTkxLjg0NSAzNi42NzUgMTgzLjMgNzQuMTNaIi8+PHBhdGggZmlsbD0iI2NjYyIgZD0iTTcxOS42NCAyNjcuOTNjNSAzLjk4IDQuMjMgMTAuNTcgMy4yOCAxNi4xNy0yMi42MyAxMzIuODItNDQuNTYgMjY2LjMyLTY2LjUzIDM5OS4yLS4yMSAxLjI0LS43NiA0LjE1LTIuNDIgNS42NS0zLjc3IDMtNy4xNyAyLjQ4LTE3LjA2LTEuMDEgMS40My0zLjM3IDIuNDgtNi45NiAzLjE0LTEwLjc4IDI1LjM0LTEyOS40MSA1MS4yNS0yNTguNjUgNzcuNzItMzg3LjcxIDIuMzMtMTMuNjUgMi4xMy05LjcxIDEuODctMjEuNVoiLz48cGF0aCBmaWxsPSIjZjJmMmYyIiBkPSJNNzE5LjY0IDI2Ny45M2MuOTIgMTAuNiAxLjA1IDUuODktMS44NyAyMS41LTI2LjQ3IDEyOS4wNi01Mi4zOCAyNTguMy03Ny43MiAzODcuNzEtLjY2IDMuODItMS43MSA3LjQxLTMuMTQgMTAuNzhxLTkxLjQ1NS0zNy40NTUtMTgzLjMtNzQuMTNjLTUuMDgtMS41OC04LjUyLjA3LTEwLjMzIDQuOTQtLjYgMzQuMTQtLjYgNjguMjkgMCAxMDIuNDMuNDMgNC4xOCAyLjY4IDYuMjggNi43NCA2LjI5LTEuMTIgMS4xNS0zLjc4IDEuMDctMy43OCAxLjA3cy0xMS45Mi0uNjktMTMuMzYtMy41NGMtLjM3LS43MS0uMzgtMS40Ni0uMzktMi4wOC0uMTgtMzYuMy0uNjEtNzIuMzktMS4yOS0xMDguMjktLjAyLS44NyAwLTEuNzIuMzUtMy4yNHMxLjM2LTMuNDYgMi43NS01LjIyYTExNjExIDExNjExIDAgMCAwIDE1OS4wNC0xOTEuMzkgMTM0MyAxMzQzIDAgMCAxLTMzLjY5IDI2LjA2QzQwMi40NCA1NTguNzggMzc2Ljc0IDU4MS42NiAzNzYuNzQgNTgxLjY2cy03MS45LTI5LjYzLTEwNi40MS00NC4yNWMtOS4xMy02LjEzLTEwLjMzLTEzLjQ3LTMuNTktMjIuMDFhNTA2MjggNTA2MjggMCAwIDAgNDI5LjQ5LTI0Ni42NGM1LjI4LTMuMjYgMTAuOTctNC40NiAxNy4wNy0zLjU5IDEuMzEuMTMgNC4xMS41OCA2LjM2IDIuNzZaIi8+PC9nPjwvc3ZnPg==&style=for-the-badge&label=Minecraft%20Sever&link=https%3A%2F%2Fstarhavensmp.mcserver.us%3A8000)

![FILES](https://img.shields.io/endpoint?style=for-the-badge&url=https%3A%2F%2Fraw.githubusercontent.com%2FExoHayvan%2FStarhavenSMP-Core%2Fmain%2F.badges%2Ffiles.json)
![LINES%20OF%20CODE](https://img.shields.io/endpoint?style=for-the-badge&url=https%3A%2F%2Fraw.githubusercontent.com%2FExoHayvan%2FStarhavenSMP-Core%2Fmain%2F.badges%2Flines_of_code.json)
![DUPLICATED%20LINES](https://img.shields.io/endpoint?style=for-the-badge&logo=sonarqubecloud&logoColor=126ED3&url=https%3A%2F%2Fraw.githubusercontent.com%2FExoHayvan%2FStarhavenSMP-Core%2Fmain%2F.badges%2Fduplicated_lines.json)

![GitHub repo size](https://img.shields.io/github/repo-size/ExoHayvan/StarhavenSMP-Core?style=for-the-badge&logo=github&logoColor=181717)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/ExoHayvan/StarhavenSMP-Core/total?style=for-the-badge&logo=github&logoColor=181717)
![GitHub last commit](https://img.shields.io/github/last-commit/ExoHayvan/StarhavenSMP-Core?style=for-the-badge&logo=github&logoColor=181717)

![RELIABILITY](https://img.shields.io/endpoint?style=for-the-badge&logo=sonarqubecloud&logoColor=126ED3&url=https%3A%2F%2Fraw.githubusercontent.com%2FExoHayvan%2FStarhavenSMP-Core%2Fmain%2F.badges%2Freliability.json)
![MAINTAINABILITY](https://img.shields.io/endpoint?style=for-the-badge&logo=sonarqubecloud&logoColor=126ED3&url=https%3A%2F%2Fraw.githubusercontent.com%2FExoHayvan%2FStarhavenSMP-Core%2Fmain%2F.badges%2Fmaintainability.json)
![SECURITY](https://img.shields.io/endpoint?style=for-the-badge&logo=sonarqubecloud&logoColor=126ED3&url=https%3A%2F%2Fraw.githubusercontent.com%2FExoHayvan%2FStarhavenSMP-Core%2Fmain%2F.badges%2Fsecurity.json)
![VULNERABILITIES](https://img.shields.io/endpoint?style=for-the-badge&logo=sonarqubecloud&logoColor=126ED3&url=https%3A%2F%2Fraw.githubusercontent.com%2FExoHayvan%2FStarhavenSMP-Core%2Fmain%2F.badges%2Fvulnerabilities.json)

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
