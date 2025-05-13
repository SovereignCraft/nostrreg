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
import org.bitcoinj.core.Bech32; // Added for Bech32 decoding

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
    private String convertNpubToHex(String input) {
        try {
            if (!input.startsWith("npub1")) {
                getLogger().info("Input is not an npub: " + input);
                return input; // Assume hex if not npub
            }

            // Decode Bech32
            Bech32.Bech32Data decoded = Bech32.decode(input);
            getLogger().info("Decoded HRP: " + decoded.hrp + ", Data length: " + decoded.data.length);

            if (!decoded.hrp.equals("npub")) {
                getLogger().warning("Invalid HRP: " + decoded.hrp);
                return null;
            }

            byte[] data5bit = decoded.data;
            if (data5bit.length != 52) {
                getLogger().warning("Unexpected data length: " + data5bit.length + ", expected 52");
                return null;
            }

            // Convert all 52 5-bit groups (260 bits) and trim to 256 bits
            byte[] data8bit = convert5to8(data5bit, 32);
            if (data8bit == null) {
                getLogger().warning("5-to-8 conversion returned null");
                return null;
            }

            if (data8bit.length != 32) {
                getLogger().warning("Invalid byte length after conversion: " + data8bit.length);
                return null;
            }

            // Convert to hex string
            StringBuilder hex = new StringBuilder();
            for (byte b : data8bit) {
                hex.append(String.format("%02x", b));
            }
            getLogger().info("Converted hex: " + hex.toString());
            return hex.toString();
        } catch (Exception e) {
            getLogger().warning("Exception in npub conversion: " + e.getMessage());
            return null;
        }
    }

    private byte[] convert5to8(byte[] data5bit, int expectedBytes) {
        int totalBits = data5bit.length * 5;
        getLogger().info("Total bits: " + totalBits);
        if (totalBits != 260) { // Expect 260 bits (256 key + 4 padding/checksum)
            getLogger().warning("Total bits not 260: " + totalBits);
            return null;
        }

        byte[] data8bit = new byte[expectedBytes];
        int byteIndex = 0;
        int bitBuffer = 0;
        int bitsRemaining = 0;

        for (int i = 0; i < data5bit.length; i++) {
            bitBuffer = (bitBuffer << 5) | (data5bit[i] & 0x1F);
            bitsRemaining += 5;

            while (bitsRemaining >= 8) {
                bitsRemaining -= 8;
                if (byteIndex < expectedBytes) { // Only fill up to 32 bytes
                    data8bit[byteIndex++] = (byte) ((bitBuffer >> bitsRemaining) & 0xFF);
                }
            }
        }

        // Log remaining bits (should be 4, discarded as checksum/padding)
        getLogger().info("Remaining bits after 256: " + bitsRemaining + ", value: " + (bitBuffer & ((1 << bitsRemaining) - 1)));

        if (byteIndex != expectedBytes) {
            getLogger().warning("Did not fill expected bytes: " + byteIndex + " vs " + expectedBytes);
            return null;
        }

        return data8bit;
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
                    sender.sendMessage("Usage: /nostrreg <npub-or-hex> [confirm]");
                    return true;
                }

                String npub = args[0]; // Get the first argument (npub)
                String hexKey = convertNpubToHex(args[0]);
                if (hexKey == null) {
                    sender.sendMessage("Invalid key format! Please provide a valid npub or hex key.");
                    return true;
                }

                // Check if the player is already registered
                if (customConfig.contains(player.getName().toLowerCase()) && (args.length < 2 || !args[1].equalsIgnoreCase("confirm"))) {
                    sender.sendMessage("You are already registered. Use /nostrreg " + npub + " confirm to overwrite.");
                    return true;
                }

                // Save the player's registration
                customConfig.set(player.getName().toLowerCase(), hexKey);
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
