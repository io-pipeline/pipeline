package io.pipeline.consul.client.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;

/**
 * Configuration for pipeline-specific Consul settings.
 */
@ConfigMapping(prefix = "pipeline")
public interface PipelineConsulConfig {
    
    /**
     * Consul-specific configuration for pipeline
     */
    @WithName("consul")
    ConsulSettings consul();
    
    /**
     * Cluster configuration
     */
    @WithName("cluster")
    ClusterSettings cluster();
    
    interface ConsulSettings {
        /**
         * The Consul host (provided by DevServices during testing)
         */
        @WithDefault("localhost")
        String host();
        
        /**
         * The Consul port (provided by DevServices during testing)
         */
        @WithDefault("8500")
        int port();
        
        /**
         * The prefix for KV store operations
         */
        @WithName("kv-prefix")
        @WithDefault("pipeline")
        String kvPrefix();
        
        /**
         * Watch configuration
         */
        @WithName("watch")
        WatchSettings watch();
        
        /**
         * Cleanup interval for zombie instances
         */
        @WithName("cleanup")
        CleanupSettings cleanup();
        
        interface WatchSettings {
            /**
             * Whether Consul watching is enabled
             */
            @WithDefault("true")
            boolean enabled();
        }
        
        interface CleanupSettings {
            /**
             * Interval between cleanup runs
             */
            @WithDefault("30m")
            Duration interval();
        }
    }
    
    interface ClusterSettings {
        /**
         * The name of the cluster
         */
        @WithDefault("default")
        String name();
    }
}