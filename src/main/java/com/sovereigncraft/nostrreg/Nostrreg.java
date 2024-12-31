package com.sovereigncraft.nostrreg;

import fi.iki.elonen.NanoHTTPD;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import com.google.gson.Gson;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class Nostrreg extends JavaPlugin {
    private FileConfiguration customConfig = null;
    private File customConfigFile = null;
    private SimpleHTTPServer server;

    @Override
    public void onEnable() {
        this.getLogger().info("SimpleWebServer Plugin Enabled");

        // Custom config file setup
        this.customConfigFile = new File(getDataFolder(), "player_data.yml");
        if (!this.customConfigFile.exists()) {
            saveResource("player_data.yml", false);
        }
        this.customConfig = YamlConfiguration.loadConfiguration(this.customConfigFile);

        try {
            server = new SimpleHTTPServer();
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            getLogger().severe("Could not start server: " + e.getMessage());
        }

        this.getCommand("nostrreg").setExecutor(this);
    }

    @Override
    public void onDisable() {
        this.getLogger().info("SimpleWebServer Plugin Disabled");
        saveCustomConfig();
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("nostrreg")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (args.length < 1) {
                    sender.sendMessage("Usage: /nostrreg <npub> [confirm]");
                    return true;
                }

                String npub = args[0];
                if (customConfig.contains(player.getName()) && (args.length < 2 || !args[1].equalsIgnoreCase("confirm"))) {
                    sender.sendMessage("You are already registered. Use /nostrreg " + npub + " confirm to overwrite.");
                    return true;
                }

                customConfig.set(player.getName(), npub);
                saveCustomConfig();
                sender.sendMessage("Your registration has been saved.");
            } else {
                sender.sendMessage("Only players can use this command.");
            }
            return true;
        }
        return false;
    }

    private void saveCustomConfig() {
        try {
            customConfig.save(customConfigFile);
        } catch (Exception e) {
            getLogger().severe("Could not save config to " + customConfigFile + ": " + e.getMessage());
        }
    }

    private class SimpleHTTPServer extends NanoHTTPD {

        public SimpleHTTPServer() {
            super(8080); // Port number
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if ("/.well-known/nostr.json".equals(uri)) {
                Map<String, String> params = session.getParms();
                String name = params.get("name");
                if (name != null) {
                    String npub = customConfig.getString(name);
                    if (npub != null) {
                        Map<String, String> jsonMap = new HashMap<>();
                        Gson gson = new Gson();
                        jsonMap.put("names", gson.toJson(Map.of(name, npub)));
                        return newFixedLengthResponse(Response.Status.OK, "application/json", new Gson().toJson(jsonMap));
                    } else {
                        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Player not registered");
                    }
                } else {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing name parameter");
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }
    }
}