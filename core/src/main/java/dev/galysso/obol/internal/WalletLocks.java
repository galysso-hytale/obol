package dev.galysso.obol.internal;

import dev.galysso.obol.api.WalletId;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One {@link ReentrantLock} per {@link WalletId}, for {@link WalletImpl}.
 *
 * <p>Per id, not per {@code Wallet} instance: two instances of the same wallet
 * must serialize on the same lock. And exactly one lock per id, not a striped
 * array: {@code WalletImpl.transferTo} takes its two locks in {@code kind:key}
 * order, which is only a deadlock-free total order if distinct ids never map
 * to the same lock in a different order. With stripes, {@code a→b} and
 * {@code c→d} where {@code a} shares a stripe with {@code d} and {@code b}
 * with {@code c} take the same two stripes in opposite orders.</p>
 *
 * <p>Locks are never evicted: the map grows with the number of distinct
 * wallets touched since startup, a few dozen bytes each.</p>
 */
public final class WalletLocks {

    private final ConcurrentMap<WalletId, ReentrantLock> locks = new ConcurrentHashMap<>();

    public Lock lockFor(WalletId id) {
        Objects.requireNonNull(id, "id");
        return locks.computeIfAbsent(id, ignored -> new ReentrantLock());
    }
}
