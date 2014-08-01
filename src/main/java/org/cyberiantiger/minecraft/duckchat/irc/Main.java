/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.duckchat.irc;

import org.cyberiantiger.minecraft.duckchat.command.SubCommand;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.cyberiantiger.minecraft.duckchat.command.PermissionException;
import org.cyberiantiger.minecraft.duckchat.command.SenderTypeException;
import org.cyberiantiger.minecraft.duckchat.command.SubCommandException;
import org.cyberiantiger.minecraft.duckchat.command.UsageException;
import org.cyberiantiger.minecraft.duckchat.irc.command.ReloadSubCommand;
import org.cyberiantiger.minecraft.duckchat.state.StateManager;

/**
 *
 * @author antony
 */
public class Main extends JavaPlugin implements Listener {

    private org.cyberiantiger.minecraft.duckchat.Main duckChat;
    private final List<IRCLink> ircLinks = new ArrayList();
    private final Timer reconnectTimer = new Timer();

    // Messages.
    private final Map<String,String> messages = new HashMap<String,String>();


    // Net
    private void connect() {
        FileConfiguration config = getConfig();
        
        if (config.isConfigurationSection("irc-bridges")) {
            ConfigurationSection bridgesSection = config.getConfigurationSection("irc-bridges");
            for (String key : bridgesSection.getKeys(false)) {
                if (!bridgesSection.isConfigurationSection(key)) {
                    continue;
                }
                ConfigurationSection bridgeSection = bridgesSection.getConfigurationSection(key);
                boolean useSsl = bridgeSection.getBoolean("ssl", false);
                String host = bridgeSection.getString("host", "localhost");
                int port = bridgeSection.getInt("port", 6667);
                String password = bridgeSection.getString("password", "");
                String nick = bridgeSection.getString("nick", "DuckChat");
                String username = bridgeSection.getString("username", "bot");
                String realm = bridgeSection.getString("realm", "localhost");
                String messageFormat = bridgeSection.getString("messageFormat", "<%s> %s");
                String actionFormat = bridgeSection.getString("actionFormat", "*%s %s");

                IRCLink ircLink = new IRCLink(this, key, useSsl, host, port, password, nick, username, realm, messageFormat, actionFormat);

                if (bridgeSection.isConfigurationSection("channels")) {
                    ConfigurationSection bridgeChannelSection = bridgeSection.getConfigurationSection("channels");
                    for (String duckChannel : bridgeChannelSection.getKeys(false)) {
                        if (bridgeChannelSection.isString(duckChannel)) {
                            ircLink.addChannel(duckChannel, bridgeChannelSection.getString(duckChannel));
                        }
                    }
                }
                ircLink.setConnected(true);
            }
        }
    }

    // Net
    private void disconnect() {
        for (IRCLink ircLink : ircLinks) {
            ircLink.setConnected(false);
        }
        ircLinks.clear();
    }

    private void load() {
        FileConfiguration config = getConfig();
        if (config.isConfigurationSection("messages")) {
            ConfigurationSection messageSection = config.getConfigurationSection("messages");
            for (String key : messageSection.getKeys(true)) {
                if (messageSection.isString(key)) {
                    messages.put(key, messageSection.getString(key).replace('&', ChatColor.COLOR_CHAR));
                }
            }
        }
    }

    @Override
    public void onEnable() {
        super.saveDefaultConfig();

        duckChat = (org.cyberiantiger.minecraft.duckchat.Main) getServer().getPluginManager().getPlugin("DuckChat");
        if (duckChat == null) {
            getLogger().severe("Disabling, DuckChat not found");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        load();
        try {
            connect();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to open channel", ex);
            disconnect();
        }
    }

    public void reload() {
        disconnect();
        reloadConfig();
        load();
        connect();
    }

    @Override
    public void onDisable() {
        reconnectTimer.cancel();
        disconnect();
    }

    private Map<String, SubCommand> subcommands = new LinkedHashMap<String, SubCommand>();
    {
        subcommands.put("reload", new ReloadSubCommand(this));
    }

    private void executeCommand(CommandSender sender, SubCommand cmd, String label, String[] args) {
        try {
            cmd.onCommand(sender, args);
        } catch (SenderTypeException ex) {
            sender.sendMessage(translate("error.wrongsender"));
        } catch (PermissionException ex) {
            sender.sendMessage(translate("error.permission", ex.getPermission()));
        } catch (UsageException ex) {
            sender.sendMessage(translate(cmd.getName() + ".usage", label));
        } catch (SubCommandException ex) {
            sender.sendMessage(translate("error.generic", ex.getMessage()));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check for label matches.
        for (Map.Entry<String,SubCommand> e : subcommands.entrySet()) {
                if(label.equalsIgnoreCase(e.getKey())) {
                    executeCommand(sender, e.getValue(), label, args);
                    return true;
                }
        }
        // Check for second argument matches.
        if (args.length >= 1) {
            for (Map.Entry<String,SubCommand> e : subcommands.entrySet()) {
                if (e.getKey().equalsIgnoreCase(args[0])) {
                    label += " " + args[0];
                    String[] newArgs = new String[args.length-1];
                    System.arraycopy(args, 1, newArgs, 0, newArgs.length);
                    executeCommand(sender, e.getValue(), label, newArgs);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        for (Map.Entry<String,SubCommand> e : subcommands.entrySet()) {
            if(label.equalsIgnoreCase(e.getKey())) {
                return e.getValue().onTabComplete(sender, args);
            } else if (args.length >= 1 && e.getKey().equalsIgnoreCase(args[0])) {
                String[] newArgs = new String[args.length-1];
                System.arraycopy(args, 1, newArgs, 0, newArgs.length);
                return e.getValue().onTabComplete(sender, newArgs);
            }
        }
        if (args.length == 1) {
            List<String> result = new ArrayList();
            String start = args[0].toLowerCase();
            for (String s : subcommands.keySet()) {
                if (s.toLowerCase().startsWith(start)) {
                    result.add(s);
                }
            }
            return result;
        }
        return null;
    }

    public Timer getReconnectTimer() {
        return reconnectTimer;
    }

    public String translate(String key, Object... args) {
        if (!messages.containsKey(key)) {
            return duckChat.translate(key, args);
        } else {
            return String.format(messages.get(key), args);
        }
    }

    public void sendChannelMessage(String identify, String targetChannel, String format) {
        duckChat.sendChannelMessage(identify, targetChannel, format);
    }

    public StateManager getState() {
        return duckChat.getState();
    }
}