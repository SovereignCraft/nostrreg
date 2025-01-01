package com.sovereigncraft.nostrreg;

// Import required libraries and classes
import fi.iki.elonen.NanoHTTPD; // Lightweight HTTP server library
import org.bukkit.command.Command; // Represents a command
import org.bukkit.command.CommandSender; // Sender of a command
import org.bukkit.configuration.file.FileConfiguration; // Interface for configuration files
import org.bukkit.configuration.file.YamlConfiguration; // YAML configuration handling
import org.bukkit.entity.Player; // Represents a player in Minecraft
import com.google.gson.Gson; // Library for handling JSON serialization and deserialization
import org.bukkit.plugin.java.JavaPlugin; // Base class for all Spigot plugins
import java.io.File; // Represents file objects
import java.io.IOException; // Exception for I/O operations
import java.util.HashMap; // HashMap for key-value storage
import java.util.Map; // Interface for maps

// Main plugin class
public final class Nostrreg extends JavaPlugin {
    // Configuration-related fields
    private FileConfiguration customConfig = null; // To manage player data configuration
    private File customConfigFile = null; // File reference for the configuration file
    private SimpleHTTPServer server; // HTTP server instance

    @Override
    public void onEnable() {
        // This method is called when the plugin is enabled
        this.getLogger().info("SimpleWebServer Plugin Enabled");

        // Setup custom configuration file (player_data.yml)
        this.customConfigFile = new File(getDataFolder(), "player_data.yml");
        if (!this.customConfigFile.exists()) {
            // If the file doesn't exist, create it from a default resource
            saveResource("player_data.yml", false);
        }
        this.customConfig = YamlConfiguration.loadConfiguration(this.customConfigFile); // Load the configuration

        // Start the HTTP server
        try {
            server = new SimpleHTTPServer();
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            // Log an error if the server fails to start
            getLogger().severe("Could not start server: " + e.getMessage());
        }

        // Register the /nostrreg command
        this.getCommand("nostrreg").setExecutor(this);
    }

    @Override
    public void onDisable() {
        // This method is called when the plugin is disabled
        this.getLogger().info("SimpleWebServer Plugin Disabled");

        // Save the custom configuration file
        saveCustomConfig();

        // Stop the HTTP server if it's running
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle the /nostrreg command
        if (command.getName().equalsIgnoreCase("nostrreg")) {
            if (sender instanceof Player) {
                // Command was sent by a player
                Player player = (Player) sender;

                // Check if the required argument is provided
                if (args.length < 1) {
                    sender.sendMessage("Usage: /nostrreg <npub> [confirm]");
                    return true;
                }

                String npub = args[0]; // Get the first argument (npub)

                // Check if the player is already registered
                if (customConfig.contains(player.getName()) && (args.length < 2 || !args[1].equalsIgnoreCase("confirm"))) {
                    sender.sendMessage("You are already registered. Use /nostrreg " + npub + " confirm to overwrite.");
                    return true;
                }

                // Save the player's registration
                customConfig.set(player.getName(), npub);
                saveCustomConfig(); // Save the configuration file
                sender.sendMessage("Your registration has been saved.");
            } else {
                // Command was not sent by a player
                sender.sendMessage("Only players can use this command.");
            }
            return true;
        }
        return false; // Command not recognized
    }

    private void saveCustomConfig() {
        // Save the custom configuration to the file
        try {
            customConfig.save(customConfigFile);
        } catch (Exception e) {
            // Log an error if saving fails
            getLogger().severe("Could not save config to " + customConfigFile + ": " + e.getMessage());
        }
    }

    // Inner class representing the HTTP server
    private class SimpleHTTPServer extends NanoHTTPD {
        public SimpleHTTPServer() {
            super(8080); // Set the server to listen on port 8080
        }

        @Override
        public Response serve(IHTTPSession session) {
            // Handle incoming HTTP requests
            String uri = session.getUri(); // Get the request URI

            if ("/.well-known/nostr.json".equals(uri)) {
                // Handle requests to /.well-known/nostr.json
                Map<String, String> params = session.getParms(); // Get request parameters
                String name = params.get("name"); // Get the "name" parameter

                if (name != null) {
                    // If the name parameter is provided, look up the player's data
                    String npub = customConfig.getString(name);

                    if (npub != null) {
                        // If the player is registered, return their npub in JSON format
                        Map<String, String> jsonMap = new HashMap<>();
                        Gson gson = new Gson();
                        jsonMap.put("names", gson.toJson(Map.of(name, npub)));
                        return newFixedLengthResponse(Response.Status.OK, "application/json", new Gson().toJson(jsonMap));
                    } else {
                        // Player not found
                        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Player not registered");
                    }
                } else {
                    // Missing "name" parameter
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing name parameter");
                }
            }

            // Default response for unrecognized endpoints
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }
    }
}
