package io.fluxzero.ticketing.booking;

import io.fluxzero.common.api.modeling.CommitModels;
import io.fluxzero.common.api.modeling.CommitModelsResult;
import io.fluxzero.common.api.modeling.ModelCommitTarget;
import io.fluxzero.sdk.configuration.client.WebSocketClient;
import io.fluxzero.sdk.persisting.eventsourcing.client.EventStoreClient;
import io.fluxzero.sdk.persisting.eventsourcing.client.ModelCommitBatchingClient;
import io.fluxzero.sdk.persisting.eventsourcing.client.ModelCommitBatchingClient.ModelCommitBatch;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Observe actual outgoing commit calls; all serialization, networking and storage remain SDK-owned. */
class MeasuredClient extends WebSocketClient {
    final AtomicInteger attempts = new AtomicInteger(), nonRetryable = new AtomicInteger();
    final AtomicInteger pending = new AtomicInteger();
    final Map<String, AtomicInteger> attemptsByCommit = new ConcurrentHashMap<>();
    final Set<String> acceptedCommits = ConcurrentHashMap.newKeySet();
    final Map<String, AtomicInteger> conflicts = new ConcurrentHashMap<>();
    final Set<String> committedReservations = ConcurrentHashMap.newKeySet();
    MeasuredClient(ClientConfig config) { super(config, null); }
    void resetMeasurements() {
        if (pending.get() != 0) throw new IllegalStateException("Cannot reset outstanding commit measurements");
        attempts.set(0);
        nonRetryable.set(0);
        attemptsByCommit.clear();
        acceptedCommits.clear();
        conflicts.clear();
        committedReservations.clear();
    }
    @Override protected EventStoreClient createEventStoreClient() {
        var delegate = super.createEventStoreClient();
        Class<?>[] interfaces = delegate instanceof ModelCommitBatchingClient
                ? new Class<?>[]{EventStoreClient.class, ModelCommitBatchingClient.class}
                : new Class<?>[]{EventStoreClient.class};
        return (EventStoreClient) Proxy.newProxyInstance(getClass().getClassLoader(), interfaces,
                (proxy, method, args) -> observe(delegate, method, args));
    }
    private Object observe(Object delegate, Method method, Object[] args) throws Throwable {
        try {
            Object returned = method.invoke(delegate, args);
            if (returned instanceof ModelCommitBatch batch) {
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ModelCommitBatch.class},
                        (proxy, batchMethod, batchArgs) -> observe(batch, batchMethod, batchArgs));
            }
            if (returned instanceof CompletableFuture<?> future
                    && (method.getName().equals("commitModels") || method.getName().equals("add"))) {
                var commit = (CommitModels) args[method.getName().equals("add") ? 1 : 0];
                attempts.incrementAndGet();
                attemptsByCommit.computeIfAbsent(commit.getCommitId(), ignored -> new AtomicInteger()).incrementAndGet();
                pending.incrementAndGet();
                return future.whenComplete((value, failure) -> pending.decrementAndGet()).thenApply(value -> {
                    var result = (CommitModelsResult) value;
                    if (result.isAccepted()) {
                        acceptedCommits.add(result.getCommitId());
                        commit.getSubsteps().forEach(step -> step.getTargets().stream()
                                .map(ModelCommitTarget::getModelId)
                                .filter(id -> id.startsWith("reservation-order-"))
                                .forEach(committedReservations::add));
                    } else {
                        conflicts.computeIfAbsent(result.getCommitId(), ignored -> new AtomicInteger()).incrementAndGet();
                        if (!result.isRetryAllowed()) nonRetryable.incrementAndGet();
                    }
                    return value;
                });
            }
            return returned;
        } catch (InvocationTargetException e) { throw e.getCause(); }
    }
}
