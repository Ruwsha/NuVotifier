package com.vexsoftware.votifier.cmd;

import com.vexsoftware.votifier.NuVotifierBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class NVReloadCmd implements CommandExecutor {

    private static final String PERMISSION = "nuvotifier.reload";

    private final NuVotifierBukkit plugin;

    public NVReloadCmd(final NuVotifierBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("このコマンドを実行する権限がありません!", NamedTextColor.DARK_RED));
            return true;
        }

        sender.sendMessage(Component.text("NuVotifierを再読み込みしています...", NamedTextColor.GRAY));
        if (plugin.reload()) {
            sender.sendMessage(Component.text("NuVotifierの再読み込みが完了しました!", NamedTextColor.DARK_GREEN));
        } else {
            sender.sendMessage(Component.text("NuVotifierの再読み込み中に問題が発生しました。コンソールを確認してください!", NamedTextColor.DARK_RED));
        }
        return true;
    }
}