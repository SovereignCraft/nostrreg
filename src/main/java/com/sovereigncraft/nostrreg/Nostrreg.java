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

        // Custom config file setup
        this.customConfigFile = new File(getDataFolder(), "player_data.yml");
        if (!this.customConfigFile.exists()) {
            // If file doesn't exist, create it with an empty configuration
            this.customConfig = new YamlConfiguration();
            try {
                this.customConfig.save(this.customConfigFile);
            } catch (IOException e) {
                getLogger().severe("Could not create player_data.yml: " + e.getMessage());
                return; // or handle this error appropriately
            }
        } else {
            // If file exists, load it
            this.customConfig = YamlConfiguration.loadConfiguration(this.customConfigFile);
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
        this.getLogger().info("Nostrreg Plugin Disabled");
        if (this.customConfig != null) { // Check if customConfig has been initialized
            saveCustomConfig();
        }
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
                // Check if the string starts with "npub1"
                /*if (npub.startsWith("npub1")) {
                    try {
                        // Decode the npub using Bech32
                        Bech32.Bech32Data decoded = Bech32.decode(npub);

                        // Ensure the human-readable part is "npub"
                        if (!"npub".equals(decoded.hrp)) {
                            throw new IllegalArgumentException("Invalid npub: incorrect HRP");
                        }

                        // Convert the Bech32 data from 5-bit to 8-bit
                        byte[] hexData = convertBits(decoded.data, 5, 8, false);

                        // Convert to hexadecimal string and print
                        String hex = bytesToHex(hexData);
                        System.out.println("Hexadecimal: " + hex);
                    } catch (Exception e) {
                        System.err.println("Error: " + e.getMessage());
                    }
                }*/
                // Check if the player is already registered
                if (customConfig.contains(player.getName().toLowerCase()) && (args.length < 2 || !args[1].equalsIgnoreCase("confirm"))) {
                    sender.sendMessage("You are already registered. Use /nostrreg " + npub + " confirm to overwrite.");
                    return true;
                }

                // Save the player's registration

                customConfig.set(player.getName().toLowerCase(), npub);
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
    /*private static byte[] convertBits(byte[] data, int fromBits, int toBits, boolean pad) throws IllegalArgumentException {
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toBits) - 1;
        byte[] ret = new byte[(data.length * fromBits + toBits - 1) / toBits];
        int j = 0;

        for (byte value : data) {
            if ((value & 0xFF) >> fromBits > 0) {
                throw new IllegalArgumentException("Invalid data value: " + value);
            }
            acc = (acc << fromBits) | value;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                ret[j++] = (byte) ((acc >> bits) & maxv);
            }
        }

        if (pad) {
            if (bits > 0) {
                ret[j++] = (byte) ((acc << (toBits - bits)) & maxv);
            }
        } else if (bits >= fromBits || ((acc << (toBits - bits)) & maxv) != 0) {
            throw new IllegalArgumentException("Invalid padding in data");
        }

        return java.util.Arrays.copyOf(ret, j);
    }

    // Converts a byte array to a hexadecimal string
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }*/
    private void saveCustomConfig() {
        if (this.customConfig != null) { // Check if customConfig is not null
            try {
                this.customConfig.save(this.customConfigFile);
            } catch (IOException e) {
                getLogger().severe("Could not save config to " + this.customConfigFile + ": " + e.getMessage());
            }
        } else {
            getLogger().severe("Custom configuration is null, cannot save.");
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
                String name = params.get("name") != null ? params.get("name").toLowerCase() : null; // Get the "name" parameter
                Response response;
                if (name != null) {
                    // If the name parameter is provided, look up the player's data
                    Nostrreg.this.customConfig = YamlConfiguration.loadConfiguration(Nostrreg.this.customConfigFile);
                    String npub = Nostrreg.this.customConfig.getString(name);

                    if (npub != null) {
                        // If the player is registered, return their npub in JSON format
                        //Map<String, String> jsonMap = new HashMap<>();
                        //Gson gson = new Gson();
                        //jsonMap.put("names", gson.toJson(Map.of(name, npub)));
                        // Create a nested map structure for JSON
                        Map<String, Map<String, String>> jsonMap = new HashMap<>();
                        Map<String, String> namesMap = new HashMap<>();
                        namesMap.put(name, npub);
                        jsonMap.put("names", namesMap);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", new Gson().toJson(jsonMap));
                    } else {
                        // Player not found
                        response = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Player " + name + " not registered");
                    }
                } else {
                    // Missing "name" parameter
                    response = newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing name parameter");
                }
                // Add CORS header
                response.addHeader("Access-Control-Allow-Origin", "*");
                return response;
            }

            // Default response for unrecognized endpoints
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }
    }
}
