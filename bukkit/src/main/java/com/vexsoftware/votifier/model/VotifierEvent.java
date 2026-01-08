package com.vexsoftware.votifier.model;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * {@code VotifierEvent} は、投票を他プラグインへ通知するための Bukkit カスタムイベントです。
 * <p>
 * 通常は Bukkit のメインスレッドへ同期的に配送され、他のプラグインが投票をリッスンできるようにします。
 * コンストラクタで {@code async=true} を指定した場合は、非同期イベントとして扱われます。
 *
 * @author frelling
 */
public class VotifierEvent extends Event {
    /**
     * イベントリスナー用の {@link HandlerList} です。
     */
    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * 内包している投票データです。
     */
    private final Vote vote;

    /**
     * 指定された投票データを内包する投票イベントを作成します。
     *
     * @param vote 投票データ
     */
    public VotifierEvent(final Vote vote) {
        this.vote = vote;
    }

    /**
     * 指定された投票データを内包する投票イベントを作成します。
     *
     * @param vote  投票データ
     * @param async 非同期で発火されるイベントかどうか
     */
    public VotifierEvent(final Vote vote, final boolean async) {
        super(async);
        this.vote = vote;
    }

    /**
     * 内包している投票データを返します。
     *
     * @return 投票データ
     */
    public Vote getVote() {
        return vote;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
