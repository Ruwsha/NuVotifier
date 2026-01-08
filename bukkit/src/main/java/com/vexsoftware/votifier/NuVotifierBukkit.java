/*
 * Copyright (C) 2012 Vex Software LLC
 * This file is part of Votifier.
 *
 * Votifier is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Votifier is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Votifier.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.vexsoftware.votifier;

import com.vexsoftware.votifier.cmd.NVReloadCmd;
import com.vexsoftware.votifier.cmd.TestVoteCmd;
import com.vexsoftware.votifier.forwarding.BukkitPluginMessagingForwardingSink;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import com.vexsoftware.votifier.net.VotifierServerBootstrap;
import com.vexsoftware.votifier.net.VotifierSession;
import com.vexsoftware.votifier.net.protocol.v1crypto.RSAIO;
import com.vexsoftware.votifier.net.protocol.v1crypto.RSAKeygen;
import com.vexsoftware.votifier.platform.JavaUtilLogger;
import com.vexsoftware.votifier.platform.LoggingAdapter;
import com.vexsoftware.votifier.platform.VotifierPlugin;
import com.vexsoftware.votifier.platform.scheduler.VotifierScheduler;
import com.vexsoftware.votifier.support.forwarding.ForwardedVoteListener;
import com.vexsoftware.votifier.support.forwarding.ForwardingVoteSink;
import com.vexsoftware.votifier.util.IOUtil;
import com.vexsoftware.votifier.util.KeyCreator;
import com.vexsoftware.votifier.util.TokenUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Votifier のメインの Bukkit プラグインクラスです。
 *
 * @author Blake Beaupain
 * @author Kramer Campbell
 */
public class NuVotifierBukkit extends JavaPlugin implements VoteHandler, VotifierPlugin, ForwardedVoteListener {

    /**
     * サーバーのブートストラップです。
     */
    private VotifierServerBootstrap bootstrap;

    /**
     * RSA 鍵ペアです。
     */
    private KeyPair keyPair;

    /**
     * デバッグモードのフラグです。
     */
    private boolean debug;

    /**
     * 投票サイトごとのトークン（Key）です。
     */
    private final Map<String, Key> tokens = new HashMap<>();

    private ForwardingVoteSink forwardingMethod;
    private VotifierScheduler scheduler;
    private LoggingAdapter pluginLogger;
    private boolean isFolia;

    private boolean loadAndBind() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            isFolia = true;

            getLogger().info("Foliaを使用しています。VotifierEvent は非同期で発火されます。");
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

        scheduler = new BukkitScheduler(this);
        pluginLogger = new JavaUtilLogger(getLogger());
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdir()) {
                throw new RuntimeException("プラグインのデータフォルダを作成できません: " + getDataFolder());
            }
        }

        // Handle configuration.
        File config = new File(getDataFolder(), "config.yml");

        /*
         * Use IP address from server.properties as a default for
         * configurations. Do not use InetAddress.getLocalHost() as it most
         * likely will return the main server address instead of the address
         * assigned to the server.
         */
        String hostAddr = Bukkit.getServer().getIp();
        if (hostAddr == null || hostAddr.isEmpty())
            hostAddr = "0.0.0.0";

        /*
         * Create configuration file if it does not exists; otherwise, load it
         */
        if (!config.exists()) {
            try {
                // First time run - do some initialization.
                getLogger().info("初回起動のため、NuVotifier を設定しています...");

                // Initialize the configuration file.
                if (!config.createNewFile()) {
                    throw new IOException("設定ファイルを作成できません: " + config);
                }

                // Load and manually replace variables in the configuration.
                String cfgStr = new String(IOUtil.readAllBytes(getResource("bukkitConfig.yml")), StandardCharsets.UTF_8);
                String token = TokenUtil.newToken();
                cfgStr = cfgStr.replace("%default_token%", token).replace("%ip%", hostAddr);
                Files.copy(new ByteArrayInputStream(cfgStr.getBytes(StandardCharsets.UTF_8)), config.toPath(), StandardCopyOption.REPLACE_EXISTING);

                /*
                 * Remind hosted server admins to be sure they have the right
                 * port number.
                 */
                getLogger().info("------------------------------------------------------------------------------");
                getLogger().info("NuVotifier はポート 8192 で待ち受けるように設定されました。CraftBukkit を共有サーバーで");
                getLogger().info("運用している場合は、ホスティング会社にこのポートが利用可能か確認してください。");
                getLogger().info("多くの場合ホスティング会社から別のポートが割り当てられるため、その場合は config.yml で");
                getLogger().info("ポート番号を変更してください。");
                getLogger().info("------------------------------------------------------------------------------");
                getLogger().info("デフォルトの NuVotifier トークンは " + token + " です。");
                getLogger().info("投票サイトへサーバーを登録する際に、このトークンが必要になります。");
                getLogger().info("------------------------------------------------------------------------------");
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "設定ファイルの作成中にエラーが発生しました", ex);
                return false;
            }
        }

        YamlConfiguration cfg;
        File rsaDirectory = new File(getDataFolder(), "rsa");

        // Load configuration.
        cfg = YamlConfiguration.loadConfiguration(config);

        /*
         * Create RSA directory and keys if it does not exist; otherwise, read
         * keys.
         */
        try {
            if (!rsaDirectory.exists()) {
                if (!rsaDirectory.mkdir()) {
                    throw new RuntimeException("RSA キーフォルダを作成できません: " + rsaDirectory);
                }
                keyPair = RSAKeygen.generate(2048);
                RSAIO.save(rsaDirectory, keyPair);
            } else {
                keyPair = RSAIO.load(rsaDirectory);
            }
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "設定ファイルまたは RSA トークンの読み込みに失敗しました", ex);
            return false;
        }

        // the quiet flag always runs priority to the debug flag
        if (cfg.isBoolean("quiet")) {
            debug = !cfg.getBoolean("quiet");
        } else {
            // otherwise, default to being noisy
            debug = cfg.getBoolean("debug", true);
        }

        // Load Votifier tokens.
        ConfigurationSection tokenSection = cfg.getConfigurationSection("tokens");

        if (tokenSection != null) {
            Map<String, Object> websites = tokenSection.getValues(false);
            for (Map.Entry<String, Object> website : websites.entrySet()) {
                tokens.put(website.getKey(), KeyCreator.createKeyFrom(website.getValue().toString()));
                getLogger().info("サイト用トークンを読み込みました: " + website.getKey());
            }
        } else {
            String token = TokenUtil.newToken();
            tokenSection = cfg.createSection("tokens");
            tokenSection.set("default", token);
            tokens.put("default", KeyCreator.createKeyFrom(token));
            try {
                cfg.save(config);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Votifier トークンの生成中にエラーが発生しました", e);
                return false;
            }
            getLogger().info("------------------------------------------------------------------------------");
            getLogger().info("設定ファイルに tokens が見つからなかったため、新しいトークンを生成しました。");
            getLogger().info("デフォルトの Votifier トークンは " + token + " です。");
            getLogger().info("投票サイトへサーバーを登録する際に、このトークンが必要になります。");
            getLogger().info("------------------------------------------------------------------------------");
        }

        // Initialize the receiver.
        final String host = cfg.getString("host", hostAddr);
        final int port = cfg.getInt("port", 8192);
        if (!debug)
            getLogger().info("QUIET モードが有効になっています。");

        if (port >= 0) {
            final boolean disablev1 = cfg.getBoolean("disable-v1-protocol");
            if (disablev1) {
                getLogger().info("------------------------------------------------------------------------------");
                getLogger().info("Votifier プロトコル v1 の解析が無効化されました。");
                getLogger().info("現在、多くの投票サイトは NuVotifier の最新プロトコルをサポートしていません。");
                getLogger().info("------------------------------------------------------------------------------");
            }

            this.bootstrap = new VotifierServerBootstrap(host, port, this, disablev1);
            this.bootstrap.start(error -> {});
        } else {
            getLogger().info("------------------------------------------------------------------------------");
            getLogger().info("Votifier のポートが 0 未満に設定されているため、ポート待ち受けサーバーを起動しません。");
            getLogger().info("この状態では、投票は PluginMessaging による転送経由でのみ受け付けます。");
            getLogger().info("------------------------------------------------------------------------------");
        }

        ConfigurationSection forwardingConfig = cfg.getConfigurationSection("forwarding");
        if (forwardingConfig != null) {
            String method = forwardingConfig.getString("method", "none").toLowerCase();
            if ("none".equals(method)) {
                getLogger().info("投票転送方式に 'none' が選択されています: フォワーダーからの投票は受信しません。");
            } else if ("pluginmessaging".equals(method)) {
                String channel = forwardingConfig.getString("pluginMessaging.channel", "NuVotifier");
                try {
                    forwardingMethod = new BukkitPluginMessagingForwardingSink(this, channel, this);
                    getLogger().info("PluginMessaging チャンネル '" + channel + "' で投票を受信します。");
                } catch (RuntimeException e) {
                    getLogger().log(Level.SEVERE, "投票転送のための PluginMessaging を設定できませんでした!", e);
                }
            } else {
                getLogger().severe("投票転送方法 '" + method + "' は不明です。デフォルトの noop 実装を使用します。");
            }
        }
        return true;
    }

    private void halt() {
        // Shut down the network handlers.
        if (bootstrap != null) {
            bootstrap.shutdown();
            bootstrap = null;
        }

        if (forwardingMethod != null) {
            forwardingMethod.halt();
            forwardingMethod = null;
        }
    }

    @Override
    public void onEnable() {
        getCommand("nvreload").setExecutor(new NVReloadCmd(this));
        getCommand("testvote").setExecutor(new TestVoteCmd(this));

        if (!loadAndBind()) {
            gracefulExit();
            setEnabled(false); // safer to just bomb out
        }
    }

    @Override
    public void onDisable() {
        halt();
        getLogger().info("Votifier を無効化しました。");
    }

    public boolean reload() {
        try {
            halt();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "停止処理中に例外が発生しました（問題ない場合もあります）", ex);
        }

        if (loadAndBind()) {
            getLogger().info("リロードが完了しました。");
            return true;
        } else {
            try {
                halt();
                getLogger().log(Level.SEVERE, "リロード中に設定の問題が発生しました。現在 Votifier は動作していません!");
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "リロード中に問題が発生し、停止処理にも失敗しました。Votifier は不安定な状態です!", ex);
            }
            return false;
        }
    }

    private void gracefulExit() {
        getLogger().log(Level.SEVERE, "Votifier の初期化に失敗しました!");
    }

    @Override
    public LoggingAdapter getPluginLogger() {
        return pluginLogger;
    }

    @Override
    public VotifierScheduler getScheduler() {
        return scheduler;
    }

    public boolean isDebug() {
        return debug;
    }

    @Override
    public Map<String, Key> getTokens() {
        return tokens;
    }

    @Override
    public KeyPair getProtocolV1Key() {
        return keyPair;
    }

    @Override
    public void onVoteReceived(final Vote vote, VotifierSession.ProtocolVersion protocolVersion, String remoteAddress) {
        if (debug) {
            getLogger().info(remoteAddress + " から " + protocolVersion.humanReadable + " の投票レコードを受信しました -> " + vote);
        }
        fireVotifierEvent(vote);
    }

    @Override
    public void onError(Throwable throwable, boolean alreadyHandledVote, String remoteAddress) {
        if (debug) {
            if (alreadyHandledVote) {
                getLogger().log(Level.WARNING, remoteAddress + " からの投票を処理しましたが、例外が発生しました", throwable);
            } else {
                getLogger().log(Level.WARNING, remoteAddress + " からの投票を処理できませんでした", throwable);
            }
        } else if (!alreadyHandledVote) {
            getLogger().log(Level.WARNING, remoteAddress + " からの投票を処理できませんでした");
        }
    }

    @Override
    public void onForward(final Vote v) {
        if (debug) {
            getLogger().info("転送された投票を受信しました -> " + v);
        }
        fireVotifierEvent(v);
    }

    private void fireVotifierEvent(Vote vote) {
        if (VotifierEvent.getHandlerList().getRegisteredListeners().length == 0) {
            getLogger().log(Level.SEVERE, "投票を受信しましたが、それをリスンするリスナーが設定されていません。");
            getLogger().log(Level.SEVERE, "設定可能なリスナーのリストについては、以下を参照してください:");
            getLogger().log(Level.SEVERE, "https://github.com/NuVotifier/NuVotifier/wiki/Setup-Guide#vote-listeners");
        }

        if (!isFolia) {
            getServer().getScheduler().runTask(
                    this, () -> getServer().getPluginManager().callEvent(new VotifierEvent(vote))
            );
        } else {
            getServer().getScheduler().runTaskAsynchronously(
                    this, () -> getServer().getPluginManager().callEvent(new VotifierEvent(vote, true))
            );
        }
    }
}
