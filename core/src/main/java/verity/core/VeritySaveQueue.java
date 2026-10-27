package verity.core;

import java.io.IOException;
import java.util.concurrent.*;

/** Single writer, with at most one pending snapshot. Coalesced callers share a durability future. */
public final class VeritySaveQueue implements AutoCloseable {
    @FunctionalInterface public interface Writer { void save(VerityWorldState.Snapshot snapshot) throws IOException; }
    private final Writer writer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("verity-save").factory());
    private VerityWorldState.Snapshot pending;
    private CompletableFuture<Void> pendingFuture;
    private CompletableFuture<Void> latest = CompletableFuture.completedFuture(null);
    private boolean running, closed;
    private Exception failure;
    public VeritySaveQueue(Writer writer) { this.writer = java.util.Objects.requireNonNull(writer); }

    public synchronized CompletableFuture<Void> save(VerityWorldState.Snapshot snapshot) {
        if (closed) return CompletableFuture.failedFuture(new IOException("Save queue is closed"));
        if (failure != null) return CompletableFuture.failedFuture(failure);
        pending = java.util.Objects.requireNonNull(snapshot);
        if (pendingFuture == null) pendingFuture = new CompletableFuture<>();
        latest = pendingFuture;
        if (!running) { running = true; executor.execute(this::writeLoop); }
        return latest;
    }
    private void writeLoop() {
        while (true) {
            VerityWorldState.Snapshot snapshot;
            CompletableFuture<Void> saved;
            synchronized (this) {
                if (pending == null) { running = false; return; }
                snapshot = pending; saved = pendingFuture; pending = null; pendingFuture = null;
            }
            try { writer.save(snapshot); saved.complete(null); }
            catch (Exception error) {
                synchronized (this) {
                    failure = error; saved.completeExceptionally(error);
                    if (pendingFuture != null) pendingFuture.completeExceptionally(error);
                    pending = null; pendingFuture = null; running = false;
                }
                return;
            }
        }
    }
    @Override public void close() {
        CompletableFuture<Void> saved;
        synchronized (this) { closed = true; saved = latest; }
        try { saved.join(); }
        finally { executor.close(); }
    }
}
