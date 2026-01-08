package com.vexsoftware.votifier.cmd;

import com.vexsoftware.votifier.NuVotifierBukkit;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.net.VotifierSession;
import com.vexsoftware.votifier.util.ArgsToVote;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class TestVoteCmd implements CommandExecutor {

    private static final String PERMISSION = "nuvotifier.testvote";
    private static final String USAGE_HINT = "使い方: /testvote [username] [serviceName=?] [username=?] [address=?] [localTimestamp=?] [timestamp=?]";

    private final NuVotifierBukkit plugin;

    public TestVoteCmd(final NuVotifierBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("このコマンドを実行する権限がありません!", NamedTextColor.DARK_RED));
            return true;
        }

        final Vote vote;
        try {
            vote = ArgsToVote.parse(args);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("テスト投票の作成中に引数の解析エラーが発生しました: " + e.getMessage(), NamedTextColor.DARK_RED));
            sender.sendMessage(Component.text(USAGE_HINT, NamedTextColor.GRAY));
            return true;
        }

        plugin.onVoteReceived(vote, VotifierSession.ProtocolVersion.TEST, "localhost.test");
        sender.sendMessage(Component.text("テスト投票を実行しました: " + vote, NamedTextColor.GREEN));
        return true;
    }
}

