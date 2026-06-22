/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs deferred background updates on a single dedicated worker thread.
 * <p>
 * A single-thread executor is used so that deferred persistence writes are
 * serialized (avoiding connection contention and unpredictable ordering). The
 * underlying queue is unbounded, so submitted tasks are never rejected and no
 * unmanaged fallback threads are spawned. Suitable for short, I/O-bound updates
 * such as the account-wide highestLevelAchieved save.
 *
 * @author GIANTCRAB
 */
public class BackgroundUpdateManager {
    private static final Logger log = LoggerFactory.getLogger(BackgroundUpdateManager.class);
    private static final long SHUTDOWN_AWAIT_MINUTES = 5;
    private static final BackgroundUpdateManager instance = new BackgroundUpdateManager();

    public static BackgroundUpdateManager getInstance() {
        return instance;
    }

    private ExecutorService executor;

    private BackgroundUpdateManager() {}

    public void start() {
        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "BackgroundUpdateWorker-" + counter.incrementAndGet());
                t.setUncaughtExceptionHandler((thread, throwable) ->
                        log.error("Uncaught exception in {}", thread.getName(), throwable));
                return t;
            }
        });
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_AWAIT_MINUTES, TimeUnit.MINUTES)) {
                log.warn("BackgroundUpdateManager did not terminate within {} minutes, forcing shutdown", SHUTDOWN_AWAIT_MINUTES);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while awaiting BackgroundUpdateManager shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
