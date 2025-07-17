package com.rokkon.pipeline.consul.watch;

/**
 * Service interface for watching Consul key-value changes.
 * This interface allows for mocking during tests and provides
 * a clean separation between the watch implementation and consumers.
 */
public interface ConsulWatchService {
    
    /**
     * Start watching Consul for changes to pipeline configurations.
     */
    void startWatching();
    
    /**
     * Stop all active watches.
     */
    void stopWatching();
    
    /**
     * Check if the watch service is currently running.
     * @return true if watches are active, false otherwise
     */
    boolean isRunning();
    
    /**
     * Get the number of active watches.
     * @return the count of active watches
     */
    int getActiveWatchCount();
}