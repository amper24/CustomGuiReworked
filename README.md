# CustomGuiReworked

Plugin for creating custom GUI interfaces in Minecraft with a convenient API for other plugins.

## Features

- **GUI Creation** - Built-in editor for creating interfaces
- **Developer API** - Simple API for integration with other plugins
- **Slot Type Detection** - Automatic detection of slot types (design, container, craft, result, fuel)
- **Multiple Storage Types** - Block, personal, global, team, temporary
- **Commands** - Convenient commands for GUI management

## Installation

1. Download the JAR file
2. Place it in the `plugins/` folder
3. Restart the server

## Commands

- `/gui create <name>` - Create a new GUI
- `/gui edit <name>` - Edit GUI
- `/gui open <name>` - Open GUI
- `/gui delete <name>` - Delete GUI
- `/gui reload` - Reload plugin

## API for Developers

```java
// Check API availability
if (!CustomGuiAPI.isInitialized()) {
    return;
}

// Create GUI
Gui gui = CustomGuiAPI.createGui("shop");
gui.setTitle("Shop");
gui.setSlots(27);
CustomGuiAPI.saveGui(gui);

// Open for player
CustomGuiAPI.openGui(player, "shop");

// Determine slot type
if (CustomGuiAPI.isDesignSlot(slot, inventory)) {
    // Design slot
}
```

## Requirements

- Java 17+
- Spigot/Paper 1.20+
- Administrator permissions

## License

MIT License
