# ExoMarketPlugin

**ExoMarketPlugin** is a dynamic, data-driven market system for Minecraft servers.  
Prices automatically shift based on global supply, demand, and player activity—creating a living economy that evolves with your server.

## 🌟 Features

- **Market GUI** – Clean and intuitive interface for buying and selling items.
- **Dynamic Pricing Engine** – Prices adjust automatically based on market supply, demand, and total circulating economy.
- **Economy Integration** – Currently integrates with **Essentials**; **Vault support** coming in a future update for wider compatibility.
- **Web Dashboard (New!)** – A live market analytics dashboard available at YOUIP:6969
  Example **http://starhaven.mcserver.us:6969**
- **PlaceholderAPI Support (New!)** – Global and player-specific economic statistics.

## 🧩 PlaceholderAPI Placeholders

### **Global Market Stats**
| Placeholder | Description |
|------------|-------------|
| `%exomarket_total_items_bought%` | Total items bought globally. |
| `%exomarket_total_items_sold%` | Total items sold globally. |
| `%exomarket_total_money_spent%` | Total money players have spent. |
| `%exomarket_total_money_earned%` | Total money players have earned. |
| `%exomarket_total_listed_items%` | Total items currently listed on the market. |

### **Per-Player Stats**
| Placeholder | Description |
|------------|-------------|
| `%exomarket_player_items_bought%` | Items bought by the player. |
| `%exomarket_player_items_sold%` | Items sold by the player. |
| `%exomarket_player_money_spent%` | Total money the player has spent. |
| `%exomarket_player_money_earned%` | Total money the player has earned. |
| `%exomarket_player_listed_items%` | Items the player currently has listed. |

## 🖥️ Web UI (Port 6969)

ExoMarketPlugin includes a built-in lightweight web server that provides:

- Still in early dev, shows items and players selling.

**URL:** `http://<server-ip>:6969`

## 📦 Installation

1. Download the latest `ExoMarketPlugin.jar` from the **Releases** page.
2. Place it into your server’s `plugins/` folder.
3. Restart the server.
4. Configure settings in `plugins/ExoMarketPlugin/config.yml` and optionally enable/secure the Web UI.

## 📝 Commands

| Command | Description |
|--------|-------------|
| `/market` | Opens the market GUI. |
| `/market sell <amount>` | Sells the specified amount of the held item. |

## ⚙️ Configuration

- **Database:**  
  Automatically uses SQLite (`ExoMarket.db`) stored in the plugin directory.
- **Economy:**  
  Currently supports **Essentials**.  
  **Vault** integration will be required for future versions to support other economy plugins.
- **Web UI:**  
  Can be enabled/disabled and configured in `config.yml`.

## 🔧 Dependencies

- **Essentials** – Used for handling economy transactions.  
- **Vault** – *(Required for expanded economy support in future updates)*  
- **PlaceholderAPI** – For all server-wide and player-specific placeholders.

## 🤝 Contributing

1. Fork the repository.  
2. Create a feature branch:  
   `git checkout -b feature-name`  
3. Commit changes:  
   `git commit -m "Description of change"`  
4. Push the branch:  
   `git push origin feature-name`  
5. Open a Pull Request.

## 🐛 Reporting Issues

If you discover bugs or want to request features, please open an issue on the **Issues** page and include as much detail as possible.

## 📜 License

This project is licensed under the **MIT License**.  
See the [LICENSE](LICENSE) file for full details.

## 👑 Credits

- **ExoHayvan** — Creator and maintainer of ExoMarketPlugin.  
- **Community Contributors** — For improvements, suggestions, and reporting issues.
