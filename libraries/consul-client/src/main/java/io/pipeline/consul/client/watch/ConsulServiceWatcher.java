package io.pipeline.consul.client.watch;

import io.pipeline.api.events.cache.ConsulModuleHealthChanged;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.ext.consul.*;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches Consul service catalog for module health changes.
 * This complements the KV watcher by monitoring the health status of registered modules.
 * 
 * TODO: Add comprehensive tests for the service watch functionality
 * TODO: Consider making this start on-demand rather than at startup
 */
@ApplicationScoped
public class ConsulServiceWatcher {

    private static final Logger LOG = Logger.getLogger(ConsulServiceWatcher.class);

    @Inject
    io.vertx.mutiny.ext.consul.ConsulClient connectionManager;

    @Inject
    Vertx vertx;

    @Inject
    Event<ConsulModuleHealthChanged> moduleHealthChangedEvent;

    @ConfigProperty(name = "pipeline.consul.watch.enabled", defaultValue = "true")
    boolean watchEnabled;


    // Track active watches
    private final List<Watch<ServiceEntryList>> activeWatches = new ArrayList<>();
    private final Map<String, ServiceHealthState> lastHealthStates = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeenTimestamp = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    /**
     * Default constructor for CDI.
     */
    public ConsulServiceWatcher() {
        // Default constructor for CDI
    }

    /**
     * Start watching services after a delay
     */
    void onStart(@Observes StartupEvent ev) {
        if (!watchEnabled) {
            return;
        }

        // Start after ConsulWatcher has initialized
        Uni.createFrom().voidItem()
            .onItem().delayIt().by(Duration.ofSeconds(10))
            .subscribe().with(
                item -> {
                    if (!running) {
                        startWatching();
                    }
                },
                error -> LOG.error("Failed to schedule service watch startup", error)
            );
    }

    /**
     * Stop watches on shutdown
     */
    void onStop(@Observes ShutdownEvent ev) {
        stopWatching();
    }

    /**
     * Start watching module services
     */
    private synchronized void startWatching() {
        if (running) {
            return;
        }
        LOG.info("Starting Consul service watches");
        running = true;
        // Watch all services with "module" tag
        watchModuleServices(connectionManager);
        LOG.info("Service watches started");
    }

    /**
     * Stop all watches
     */
    private synchronized void stopWatching() {
        if (!running) {
            return;
        }

        LOG.info("Stopping Consul service watches");
        running = false;

        activeWatches.forEach(Watch::stop);
        activeWatches.clear();
        lastHealthStates.clear();
        lastSeenTimestamp.clear();
    }

    /**
     * Watch services tagged with "module"
     */
    private void watchModuleServices(io.vertx.mutiny.ext.consul.ConsulClient client) {
        // Option 1: Periodic query for services with "module" tag
        // This is more reliable than trying to watch all services at once
        
        LOG.info("Starting periodic health monitoring for module services");
        
        // Schedule periodic health checks every 30 seconds
        vertx.setPeriodic(30000, timerId -> {
            if (!running) {
                vertx.cancelTimer(timerId);
                return;
            }
            
            queryModuleServiceHealth(client);
            cleanupStaleServices();
        });
        
        // Initial query
        queryModuleServiceHealth(client);
    }

    /**
     * Query Consul for all services with "module" tag and check their health
     */
    private void queryModuleServiceHealth(io.vertx.mutiny.ext.consul.ConsulClient client) {
        // Get all services from catalog, then filter by tag
        client.catalogServices()
            .subscribe().with(
                serviceList -> {
                    if (serviceList != null && serviceList.getList() != null) {
                        // catalogServices() returns ServiceList with List<Service> objects
                        serviceList.getList().stream()
                            .filter(service -> service.getTags() != null && 
                                          service.getTags().contains("module"))
                            .forEach(service -> {
                                String serviceName = service.getName();
                                queryServiceHealth(client, serviceName);
                            });
                    }
                },
                error -> LOG.errorf("Failed to query Consul services: %s", error.getMessage())
            );
    }

    /**
     * Query health for a specific service
     */
    private void queryServiceHealth(io.vertx.mutiny.ext.consul.ConsulClient client, String serviceName) {
        client.healthServiceNodes(serviceName, false) // false = all services (passing and failing)
            .subscribe().with(
                serviceEntryList -> handleServiceHealthResult(serviceName, serviceEntryList),
                error -> LOG.errorf("Failed to query health for service %s: %s", serviceName, error.getMessage())
            );
    }

    /**
     * Handle service health query result
     */
    private void handleServiceHealthResult(String serviceName, ServiceEntryList serviceEntryList) {
        if (serviceEntryList == null || serviceEntryList.getList() == null) {
            return;
        }

        List<ServiceEntry> services = serviceEntryList.getList();
        
        long currentTime = System.currentTimeMillis();
        
        // Process each service instance
        services.forEach(entry -> {
            String serviceId = entry.getService().getId();
            
            // Update last seen timestamp
            lastSeenTimestamp.put(serviceId, currentTime);
            
            // Determine health state
            ServiceHealthState currentState = determineHealthState(entry);
            ServiceHealthState lastState = lastHealthStates.get(serviceId);
            
            // Check if state changed
            if (lastState == null || !lastState.equals(currentState)) {
                LOG.debugf("Module %s health changed: %s -> %s", 
                    serviceName, lastState, currentState);
                
                lastHealthStates.put(serviceId, currentState);
                
                // Fire health change event
                moduleHealthChangedEvent.fire(
                    new ConsulModuleHealthChanged(
                        serviceId,
                        serviceName,
                        currentState.status(),
                        currentState.reason()
                    )
                );
            }
        });
    }

    /**
     * Clean up services that haven't been seen in recent queries (likely deregistered)
     */
    private void cleanupStaleServices() {
        long currentTime = System.currentTimeMillis();
        long staleThreshold = 90000; // 90 seconds (3 query cycles)
        
        lastSeenTimestamp.entrySet().removeIf(entry -> {
            String serviceId = entry.getKey();
            long lastSeen = entry.getValue();
            
            if (currentTime - lastSeen > staleThreshold) {
                LOG.debugf("Module service removed: %s", serviceId);
                
                // Remove from health tracking
                lastHealthStates.remove(serviceId);
                
                // Fire removal event
                moduleHealthChangedEvent.fire(
                    new ConsulModuleHealthChanged(
                        serviceId,
                        "",
                        "removed",
                        "Service no longer registered"
                    )
                );
                
                return true; // Remove from lastSeenTimestamp
            }
            return false;
        });
    }

    /**
     * Handle service health changes (legacy method for watch-based approach)
     */
    private void handleServiceHealthChange(WatchResult<ServiceEntryList> result) {
        ServiceEntryList serviceList = result.nextResult();

        if (serviceList == null || serviceList.getList() == null) {
            return;
        }

        List<ServiceEntry> services = serviceList.getList();

        // Filter for module services (those with "module" tag)
        List<ServiceEntry> moduleServices = services.stream()
            .filter(entry -> entry.getService().getTags() != null && 
                            entry.getService().getTags().contains("module"))
            .toList();

        // Process each module service entry
        moduleServices.forEach(entry -> {
            String serviceId = entry.getService().getId();
            String serviceName = entry.getService().getName();

            // Determine health state
            ServiceHealthState currentState = determineHealthState(entry);
            ServiceHealthState lastState = lastHealthStates.get(serviceId);

            // Check if state changed
            if (lastState == null || !lastState.equals(currentState)) {
                LOG.debugf("Module %s health changed: %s -> %s", 
                    serviceName, lastState, currentState);

                lastHealthStates.put(serviceId, currentState);

                // Fire health change event
                moduleHealthChangedEvent.fire(
                    new ConsulModuleHealthChanged(
                        serviceId,
                        serviceName,
                        currentState.status(),
                        currentState.reason()
                    )
                );
            }
        });

        // Check for removed services
        lastHealthStates.keySet().stream()
            .filter(serviceId -> moduleServices.stream()
                .noneMatch(entry -> entry.getService().getId().equals(serviceId)))
            .forEach(removedServiceId -> {
                LOG.debugf("Module service removed: %s", removedServiceId);
                lastHealthStates.remove(removedServiceId);

                // Fire removal event
                moduleHealthChangedEvent.fire(
                    new ConsulModuleHealthChanged(
                        removedServiceId,
                        "",
                        "removed",
                        "Service no longer registered"
                    )
                );
            });
    }

    /**
     * Determine health state from service entry
     */
    private ServiceHealthState determineHealthState(ServiceEntry entry) {
        List<Check> checks = entry.getChecks();

        if (checks == null || checks.isEmpty()) {
            return new ServiceHealthState("unknown", "No health checks");
        }

        // Check all health checks
        boolean hasFailure = false;
        boolean hasWarning = false;
        StringBuilder reason = new StringBuilder();

        for (Check check : checks) {
            CheckStatus status = check.getStatus();

            if (status == CheckStatus.CRITICAL) {
                hasFailure = true;
                if (!reason.isEmpty()) reason.append("; ");
                reason.append(check.getName()).append(": ").append(check.getOutput());
            } else if (status == CheckStatus.WARNING) {
                hasWarning = true;
                if (!hasFailure && !reason.isEmpty()) reason.append("; ");
                if (!hasFailure) {
                    reason.append(check.getName()).append(": warning");
                }
            }
        }

        if (hasFailure) {
            return new ServiceHealthState("critical", reason.toString());
        } else if (hasWarning) {
            return new ServiceHealthState("warning", reason.toString());
        } else {
            return new ServiceHealthState("passing", "All checks passing");
        }
    }

    /**
     * Internal record for tracking health state
     */
    private record ServiceHealthState(String status, String reason) {}
}
