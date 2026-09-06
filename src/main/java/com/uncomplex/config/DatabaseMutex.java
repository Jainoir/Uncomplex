package com.uncomplex.config;

import com.uncomplex.exception.OperationBusyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** PostgreSQL advisory locks with a bounded wait; exact-key JVM locks for local in-memory H2. */
@Component
public class DatabaseMutex {
    private static final ConcurrentHashMap<String, LocalLock> LOCAL = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final boolean postgres;
    private final long waitMillis;

    public DatabaseMutex(JdbcTemplate jdbc, @Value("${app.concurrency.lock-wait-millis:1000}") long waitMillis) {
        this.jdbc = jdbc;
        if (waitMillis < 0 || waitMillis > 10_000) throw new IllegalArgumentException("Lock wait must be 0..10000 ms");
        this.waitMillis = waitMillis;
        String database = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        this.postgres = "PostgreSQL".equals(database);
        if (!postgres && !"H2".equals(database)) throw new IllegalStateException("Unsupported locking database: " + database);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String key) {
        if (postgres) acquirePostgres(key);
        else acquireLocal(key);
    }

    private void acquirePostgres(String key) {
        long lockId = advisoryId(key);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        do {
            Boolean acquired = jdbc.query(connection -> {
                var statement = connection.prepareStatement("select pg_try_advisory_xact_lock(?)");
                statement.setLong(1, lockId);
                statement.setQueryTimeout(1);
                return statement;
            }, result -> result.next() && result.getBoolean(1));
            if (Boolean.TRUE.equals(acquired)) return;
            if (System.nanoTime() >= deadline) break;
            try { Thread.sleep(10); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OperationBusyException();
            }
        } while (System.nanoTime() < deadline);
        throw new OperationBusyException();
    }

    // SHA-256 avoids the easy String.hashCode()/256 collisions of the previous stripes.
    static long advisoryId(String key) {
        try {
            return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8))).getLong();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private void acquireLocal(String key) {
        LocalLock entry = LOCAL.compute(key, (ignored, existing) -> {
            LocalLock value = existing == null ? new LocalLock() : existing;
            value.references++;
            return value;
        });
        boolean acquired = false;
        boolean registered = false;
        try {
            acquired = entry.lock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
            if (!acquired) throw new OperationBusyException();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    entry.lock.unlock();
                    releaseReference(key, entry);
                }
            });
            registered = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OperationBusyException();
        } finally {
            if (!registered) {
                if (acquired) entry.lock.unlock();
                releaseReference(key, entry);
            }
        }
    }

    private void releaseReference(String key, LocalLock entry) {
        LOCAL.compute(key, (ignored, existing) -> --entry.references == 0 ? null : entry);
    }

    private static final class LocalLock {
        final ReentrantLock lock = new ReentrantLock();
        int references;
    }
}