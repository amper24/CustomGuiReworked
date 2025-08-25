# 🚀 Quick Start Guide

Get CustomGuiReworked up and running in minutes!

## Prerequisites

- **Java 17** or higher
- **Spigot/Paper 1.20+** server
- **Administrator permissions**

## Installation

### 1. Download
Download the latest release from [GitHub Releases](https://github.com/your-username/CustomGuiReworked/releases)

### 2. Install
```bash
# Place the JAR file in your plugins folder
mv CustomGuiReworked.jar plugins/
```

### 3. Start Server
```bash
# Start your Minecraft server
java -jar spigot.jar
```

### 4. Verify Installation
Look for this message in your server console:
```
[INFO] CustomGuiReworked has been enabled!
[INFO] CustomGuiReworked API инициализирован
```

## First GUI

### Create a Simple Shop

1. **Join your server** with administrator permissions
2. **Create a shop GUI**:
   ```
   /gui create shop
   ```
3. **Edit the GUI**:
   ```
   /gui edit shop
   ```
4. **Open the GUI**:
   ```
   /gui open shop
   ```

### Basic Commands

| Command | Description |
|---------|-------------|
| `/gui create <name>` | Create new GUI |
| `/gui edit <name>` | Edit existing GUI |
| `/gui open <name>` | Open GUI |
| `/gui delete <name>` | Delete GUI |
| `/gui reload` | Reload plugin |

## API Integration

### For Developers

Add this to your plugin's `onEnable()`:

```java
@Override
public void onEnable() {
    // Check if CustomGuiReworked is available
    if (!CustomGuiAPI.isInitialized()) {
        getLogger().warning("CustomGuiReworked not found!");
        return;
    }
    
    // Create a shop GUI
    Gui shopGui = CustomGuiAPI.createGui("my_shop");
    shopGui.setTitle("§6My Shop");
    shopGui.setSlots(27);
    CustomGuiAPI.saveGui(shopGui);
    
    getLogger().info("Shop GUI created successfully!");
}
```

### Open GUI for Player

```java
// Open GUI for a player
CustomGuiAPI.openGui(player, "my_shop");
```

## Configuration

### Permissions

Add these permissions to your permission plugin:

```yaml
customgui.create    # Create new GUIs
customgui.edit      # Edit existing GUIs  
customgui.open      # Open GUIs
customgui.delete    # Delete GUIs
customgui.reload    # Reload configurations
```

### Config File

The plugin will create `plugins/CustomGuiReworked/config.yml` automatically.

## Troubleshooting

### Common Issues

**Plugin not loading:**
- Check Java version (requires Java 17+)
- Verify server version (requires 1.20+)
- Check console for error messages

**Commands not working:**
- Ensure you have proper permissions
- Check if plugin is enabled
- Verify command syntax

**GUI not opening:**
- Check if GUI exists: `/gui list`
- Verify permissions
- Check console for errors

### Getting Help

- **Documentation**: [Full Documentation](API_DOCUMENTATION.md)
- **Issues**: [GitHub Issues](https://github.com/your-username/CustomGuiReworked/issues)
- **Wiki**: [Project Wiki](https://github.com/your-username/CustomGuiReworked/wiki)

## Next Steps

1. **Explore the GUI Editor** - Try creating different types of interfaces
2. **Learn the API** - Read the [API Documentation](API_DOCUMENTATION.md)
3. **Join the Community** - Share your creations and get help
4. **Contribute** - Report bugs or suggest features

---

**Need more help?** Check out the [full documentation](API_DOCUMENTATION.md) or [create an issue](https://github.com/your-username/CustomGuiReworked/issues/new) on GitHub!
