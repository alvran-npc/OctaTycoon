package id.octanexus.octatycoon.economy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter point for Vault, ExcellentEconomy, or future item-reward backends.
 * PayService invokes deliver on the Bukkit main thread; complete the returned future
 * when the external reward has either been applied or definitively rejected.
 */
public interface RewardBackend {
    RewardType type();
    CompletableFuture<Boolean> deliver(UUID playerId, long amount, String source);
}
