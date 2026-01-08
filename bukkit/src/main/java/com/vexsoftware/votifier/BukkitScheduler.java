package com.vexsoftware.votifier;

import com.vexsoftware.votifier.platform.scheduler.ScheduledVotifierTask;
import com.vexsoftware.votifier.platform.scheduler.VotifierScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

final class BukkitScheduler implements VotifierScheduler {
    private final NuVotifierBukkit plugin;

    BukkitScheduler(final NuVotifierBukkit plugin) {
        this.plugin = plugin;
    }

    private static int toTicks(final int time, final TimeUnit unit) {
        return (int) (unit.toMillis(time) / 50);
    }

    @Override
    public ScheduledVotifierTask delayedOnPool(final Runnable runnable, final int delay, final TimeUnit unit) {
        return new BukkitTaskWrapper(plugin.getServer().getScheduler()
                .runTaskLaterAsynchronously(plugin, runnable, toTicks(delay, unit)));
    }

    @Override
    public ScheduledVotifierTask repeatOnPool(final Runnable runnable, final int delay, final int repeat, final TimeUnit unit) {
        return new BukkitTaskWrapper(plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, runnable, toTicks(delay, unit), toTicks(repeat, unit)));
    }

    private static final class BukkitTaskWrapper implements ScheduledVotifierTask {
        private final BukkitTask task;

        BukkitTaskWrapper(final BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }
    }
}

