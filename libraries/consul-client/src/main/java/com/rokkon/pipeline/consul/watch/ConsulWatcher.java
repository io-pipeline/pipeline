package com.rokkon.pipeline.consul.watch;

import com.rokkon.pipeline.constants.PipelineConstants;
import com.rokkon.pipeline.events.cache.ConsulClusterPipelineChangedEvent;
import com.rokkon.pipeline.events.cache.ConsulModuleRegistrationChangedEvent;
import com.rokkon.pipeline.events.cache.ConsulPipelineDefinitionChangedEvent;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.KeyValue;
import io.vertx.ext.consul.KeyValueList;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.mutiny.ext.consul.Watch;
import io.vertx.ext.consul.ConsulClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import com.rokkon.pipeline.consul.config.PipelineConsulConfig;
import com.rokkon.pipeline.consul.config.ConsulConfiguration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches Consul KV store for changes to pipeline definitions, module registrations,
 * and cluster configurations. Fires CDI events when changes are detected.
 * 
 * TODO: Add comprehensive tests for the watch functionality
 * TODO: Consider making this start on-demand rather than at startup
 */
@ApplicationScoped
public class ConsulWatcher implements ConsulWatchService {

    private static final Logger LOG = Logger.getLogger(ConsulWatcher.class);

    @Inject
    Vertx vertx;

    @Inject
    ConsulClient consulClient;

    @Inject
    Event<ConsulPipelineDefinitionChangedEvent> pipelineDefinitionChangedEvent;

    @Inject
    Event<ConsulModuleRegistrationChangedEvent> moduleRegistrationChangedEvent;

    @Inject
    Event<ConsulClusterPipelineChangedEvent> clusterPipelineChangedEvent;

    @Inject
    PipelineConsulConfig pipelineConfig;

    @Inject
    ConsulConfiguration consulConfig;

    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    // Track active watches for cleanup
    private final List<Watch<KeyValueList>> activeWatches = new ArrayList<>();
    private final Map<String, Long> lastModifyIndex = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    /**
     * Default constructor for CDI.
     */
    public ConsulWatcher() {
        // Default constructor for CDI
    }

    /**
     * Start watching Consul on startup (after initialization completes)
     */
    void onStart(@Observes StartupEvent ev) {
        if (!pipelineConfig.consul().watch().enabled()) {
            LOG.info("Consul watching disabled");
            return;
        }

        // Delay start to allow initialization to complete first
        Uni.createFrom().voidItem()
                .onItem().delayIt().by(Duration.ofSeconds(10))
                .subscribe().with(
                        item -> {
                            if (!running) {
                                startWatching();
                            }
                        },
                        error -> LOG.error("Failed to schedule Consul watch startup", error)
                );
    }

    /**
     * Stop all watches on shutdown
     */
    void onStop(@Observes ShutdownEvent ev) {
        stopWatching();
    }

    /**
     * Start watching Consul KV paths
     */
    @Override
    public synchronized void startWatching() {
        if (running) {
            return;
        }

        LOG.info("Starting Consul watches");
        running = true;

        // Use injected consul configuration instead of system properties
        String host = consulConfig.host();
        int port = consulConfig.port();

        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(host)
            .setPort(port)
            .setTimeout((int) consulConfig.timeout().toMillis());

        // Use consul config prefix if provided, otherwise use applicationName
        String appPrefix = consulConfig.kv().prefix();

        LOG.infof("Consul watch configuration - host: %s, port: %d, prefix: %s", host, port, appPrefix);

        // Watch pipeline definitions
        String pipelinePrefix = PipelineConstants.buildConsulKey(appPrefix, 
            PipelineConstants.CONSUL_PIPELINES_KEY, 
            PipelineConstants.CONSUL_DEFINITIONS_KEY);
        Watch<KeyValueList> pipelineWatch = Watch.keyPrefix(pipelinePrefix, vertx, options);
        pipelineWatch.setHandler(result -> {
            if (result.succeeded()) {
                handlePipelineDefinitionChange(result.nextResult());
            } else {
                LOG.warnf(result.cause(), "Watch failed for prefix: %s", pipelinePrefix);
            }
        }).start();
        activeWatches.add(pipelineWatch);

        // Watch module registrations
        String modulePrefix = PipelineConstants.buildConsulKey(appPrefix,
            PipelineConstants.CONSUL_MODULES_KEY,
            PipelineConstants.CONSUL_REGISTERED_KEY);
        Watch<KeyValueList> moduleWatch = Watch.keyPrefix(modulePrefix, vertx, options);
        moduleWatch.setHandler(result -> {
            if (result.succeeded()) {
                handleModuleRegistrationChange(result.nextResult());
            } else {
                LOG.warnf(result.cause(), "Watch failed for prefix: %s", modulePrefix);
            }
        }).start();
        activeWatches.add(moduleWatch);

        // Watch cluster pipelines
        String clusterPrefix = PipelineConstants.buildConsulKey(appPrefix,
            PipelineConstants.CONSUL_CLUSTERS_KEY, pipelineConfig.cluster().name(), PipelineConstants.CONSUL_PIPELINES_KEY);
        Watch<KeyValueList> clusterWatch = Watch.keyPrefix(clusterPrefix, vertx, options);
        clusterWatch.setHandler(result -> {
            if (result.succeeded()) {
                handleClusterPipelineChange(result.nextResult());
            } else {
                LOG.warnf(result.cause(), "Watch failed for prefix: %s", clusterPrefix);
            }
        }).start();
        activeWatches.add(clusterWatch);

        LOG.infof("Started %d Consul watches", activeWatches.size());
    }

    /**
     * Stop all active watches
     */
    @Override
    public synchronized void stopWatching() {
        if (!running) {
            return;
        }

        LOG.info("Stopping Consul watches");
        running = false;

        activeWatches.forEach(Watch::stop);
        activeWatches.clear();
        lastModifyIndex.clear();

        LOG.info("Consul watches stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getActiveWatchCount() {
        return activeWatches.size();
    }

    /**
     * Handle changes to pipeline definitions
     */
    private void handlePipelineDefinitionChange(KeyValueList kvList) {
        if (kvList == null || kvList.getList() == null) {
            return;
        }

        // Check if the data actually changed using modify index
        Long currentIndex = kvList.getIndex();
        Long lastIndex = lastModifyIndex.get("pipeline-definitions");

        if (lastIndex != null && lastIndex.equals(currentIndex)) {
            return; // No actual change
        }

        lastModifyIndex.put("pipeline-definitions", currentIndex);

        List<KeyValue> changes = kvList.getList();
        LOG.debugf("Pipeline definitions changed: %d keys affected", changes.size());

        // Fire event for each changed pipeline
        changes.forEach(kv -> {
            String key = kv.getKey();
            // Extract pipeline ID from key: {app-name}/pipelines/definitions/{pipelineId}
            String[] parts = key.split("/");
            if (parts.length >= 4) {
                String pipelineId = parts[3];
                // Skip metadata keys
                if (!key.endsWith("/metadata")) {
                    pipelineDefinitionChangedEvent.fire(
                            new ConsulPipelineDefinitionChangedEvent(pipelineId, kv.getValue())
                    );
                }
            }
        });
    }

    /**
     * Handle changes to module registrations
     */
    private void handleModuleRegistrationChange(KeyValueList kvList) {
        if (kvList == null || kvList.getList() == null) {
            return;
        }

        // Check if the data actually changed
        Long currentIndex = kvList.getIndex();
        Long lastIndex = lastModifyIndex.get("module-registrations");

        if (lastIndex != null && lastIndex.equals(currentIndex)) {
            return;
        }

        lastModifyIndex.put("module-registrations", currentIndex);

        List<KeyValue> changes = kvList.getList();
        LOG.debugf("Module registrations changed: %d keys affected", changes.size());

        // Fire event for each changed module
        changes.forEach(kv -> {
            String key = kv.getKey();
            // Extract module ID from key: {app-name}/modules/registered/{moduleId}
            String[] parts = key.split("/");
            if (parts.length >= 4) {
                String moduleId = parts[3];
                moduleRegistrationChangedEvent.fire(
                        new ConsulModuleRegistrationChangedEvent(moduleId, kv.getValue())
                );
            }
        });
    }

    /**
     * Handle changes to cluster pipelines
     */
    private void handleClusterPipelineChange(KeyValueList kvList) {
        if (kvList == null || kvList.getList() == null) {
            return;
        }

        // Check if the data actually changed
        Long currentIndex = kvList.getIndex();
        Long lastIndex = lastModifyIndex.get("cluster-pipelines");

        if (lastIndex != null && lastIndex.equals(currentIndex)) {
            return;
        }

        lastModifyIndex.put("cluster-pipelines", currentIndex);

        List<KeyValue> changes = kvList.getList();
        LOG.debugf("Cluster pipelines changed: %d keys affected", changes.size());

        // Fire event for each changed pipeline
        changes.forEach(kv -> {
            String key = kv.getKey();
            // Extract pipeline ID from key: {app-name}/clusters/{cluster}/pipelines/{pipelineId}/config
            String[] parts = key.split("/");
            if (parts.length >= 6) {
                String pipelineId = parts[5];
                clusterPipelineChangedEvent.fire(
                        new ConsulClusterPipelineChangedEvent(pipelineConfig.cluster().name(), pipelineId, kv.getValue())
                );
            }
        });
    }
}
