package io.pipeline.consul.client.util;

import org.testcontainers.DockerClientFactory;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;

import java.util.List;
import java.util.Map;

/**
 * Utility class to get Consul DevServices container information.
 */
public class ConsulDevServicesUtil {
    
    private static final String LABEL_QUARKUS_CONSUL = "quarkus.consul.devservices";
    private static final String LABEL_CONSUL_TYPE = "quarkus.consul.devservices.type";
    
    /**
     * Get the host and port of the running Consul agent container.
     * 
     * @return An array with [host, port] or ["localhost", "8500"] if not found
     */
    public static String[] getConsulHostPort() {
        try {
            List<Container> containers = DockerClientFactory.instance().client()
                .listContainersCmd()
                .withLabelFilter(Map.of(LABEL_QUARKUS_CONSUL, "true"))
                .withStatusFilter(List.of("running"))
                .exec();
            
            for (Container container : containers) {
                Map<String, String> labels = container.getLabels();
                String type = labels.get(LABEL_CONSUL_TYPE);
                
                if ("agent".equals(type)) {
                    // Extract port from agent container
                    for (ContainerPort port : container.getPorts()) {
                        if (port.getPrivatePort() != null && port.getPrivatePort() == 8500 && port.getPublicPort() != null) {
                            return new String[]{"localhost", String.valueOf(port.getPublicPort())};
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to find Consul DevServices container: " + e.getMessage());
        }
        
        // Fallback to system properties
        String host = System.getProperty("pipeline.consul.host", "localhost");
        String port = System.getProperty("pipeline.consul.port", "8500");
        return new String[]{host, port};
    }
}