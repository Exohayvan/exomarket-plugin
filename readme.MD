# ExoMarketPlugin

**ExoMarketPlugin** is a dynamic market management plugin for Minecraft servers, allowing players to engage in buying and selling items. Prices are automatically adjusted based on the market's supply and demand, providing an engaging economic system.

## Features

- **Market GUI**: A user-friendly graphical interface for viewing, buying, and selling items.
- **Dynamic Pricing**: Prices automatically adjust based on market supply, demand, and the overall economy.
- **Economy Integration**: Currently compatible with Essentials for handling transactions, with Vault support coming in future updates.
- **Simple Commands**: Easy-to-use commands for opening the market and selling items.

## Installation

1. Download the latest `ExoMarketPlugin.jar` from the [Releases](#) page.
2. Place the `jar` file into the `plugins` folder of your Minecraft server.
3. Restart the server to load the plugin.
4. Configure the plugin settings in the generated `plugins/ExoMarketPlugin/config.yml` file.

## Commands

| Command                  | Description                         |
| ------------------------ | ----------------------------------- |
| `/market`                | Opens the market GUI.               |
| `/market sell <amount>`  | Sells the specified amount of items. |

## Configuration

- **Database**: The plugin uses an SQLite database to store market data, which is automatically created in the `plugins/ExoMarketPlugin` folder.
- **Economy Integration**: Currently supports Essentials. Vault is required for future updates to ensure full compatibility with other economy plugins.

## Dependencies

- **Essentials**: For current economy integration.
- **Vault**: Required for future economy plugin support.

## Contributing

Contributions are welcome! Follow these steps to contribute:

1. Fork the repository.
2. Create a new branch (`git checkout -b feature-branch`).
3. Commit your changes (`git commit -m "Add new feature"`).
4. Push to your branch (`git push origin feature-branch`).
5. Open a Pull Request with a description of your changes.

## Reporting Issues

If you encounter any issues or have feature requests, please open an issue on the [Issues](#) page with details.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more information.

## Credits

- **[Your Name]**: Creator and maintainer of ExoMarketPlugin.
- **Community Contributors**: For submitting fixes and improvements.
