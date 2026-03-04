package io.elysian.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-JVM distributed lock manager (single-node / test mode).
 *
 * <p>In production replace with the ZooKeeper implementation that creates
 * ephemeral sequential nodes under {@code /elysian/locks/{operatorId}}.
 * The interface is identical so only the wiring in ElysianMain changes.
 */
public class DistributedLockManager {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedLockManager.class);

    private final Map<String, ReentrantLock> lockMap  = new ConcurrentHashMap<>();
    private final Map<String, String>        tokenMap = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire a key-scoped lock.
     *
     * @param key       lock scope (usually operatorId)
     * @param timeoutMs maximum wait time
     * @return a lock token string if successful, {@code null} if timeout
     */
    public String acquireLock(String key, long timeoutMs) {
        ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock(true));
        try {
            boolean acquired = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
            if (acquired) {
                String token = UUID.randomUUID().toString();
                tokenMap.put(token, key);
                LOG.debug("[LOCK] acquired key={} token={}", key, token);
                return token;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.debug("[LOCK] timeout waiting for key={}", key);
        return null;
    }

    /**
     * Releases a previously acquired lock.
     *
     * @param token the token returned by {@link #acquireLock}
     */
    public void releaseLock(String token) {
        String key = tokenMap.remove(token);
        if (key == null) return;
        ReentrantLock lock = lockMap.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            LOG.debug("[LOCK] released key={} token={}", key, token);
        }
    }
}
