package io.pipeline.api.events.cache;

import io.pipeline.api.model.ModuleRegistration;

import java.util.Set;

/**
 * CDI event fired when Consul state has been restored on engine startup.
 * This indicates that the engine has successfully loaded its state from Consul.
 */
public record ConsulStateRestored(
    Set<ModuleRegistration> restoredModules
) {}