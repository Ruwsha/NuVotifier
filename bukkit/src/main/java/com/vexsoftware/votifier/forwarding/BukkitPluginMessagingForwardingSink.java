package com.vexsoftware.votifier.forwarding;

import com.vexsoftware.votifier.support.forwarding.AbstractPluginMessagingForwardingSink;
import com.vexsoftware.votifier.support.forwarding.ForwardedVoteListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Objects;
import java.util.logging.Level;

/**
 * PluginMessaging を使って投票を受信するための、Bukkit 実装のフォワーディング Sink です。
 *
 * @author Joe Hirschfeld
 */
public final class BukkitPluginMessagingForwardingSink extends AbstractPluginMessagingForwardingSink implements PluginMessageListener {

    private final Plugin plugin;
    private final String channel;

    public BukkitPluginMessagingForwardingSink(final Plugin plugin, final String channel, final ForwardedVoteListener listener) {
        super(listener);
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.channel = Objects.requireNonNull(channel, "Channel cannot be null");
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, this);
    }

    @Override
    public void halt() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, channel, this);
    }

    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] data) {
        try {
            handlePluginMessage(data);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "転送された投票の処理中に不明なエラーが発生しました。", e);
        }
    }
}
