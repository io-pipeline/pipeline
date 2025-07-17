package com.rokkon.pipeline.consul.watch;

import io.quarkus.test.Mock;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.jboss.logging.Logger;

/**
 * Mock implementation of ConsulWatchService for integration tests.
 * This prevents the real ConsulWatcher from starting and interfering with tests.
 */
@Mock
@Alternative
@Priority(1)
@ApplicationScoped
public class MockConsulWatchService implements ConsulWatchService {
    
    private static final Logger LOG = Logger.getLogger(MockConsulWatchService.class);
    
    private boolean running = false;
    
    @Override
    public synchronized void startWatching() {
        LOG.info("Mock ConsulWatchService: startWatching called (no-op)");
        running = true;
    }
    
    @Override
    public synchronized void stopWatching() {
        LOG.info("Mock ConsulWatchService: stopWatching called (no-op)");
        running = false;
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
    
    @Override
    public int getActiveWatchCount() {
        return 0; // No actual watches in mock
    }
}