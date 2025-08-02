package io.pipeline.schemamanager;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class OpenSearchTestResource implements QuarkusTestResourceLifecycleManager {

    private OpensearchContainer opensearch;

    @Override
    public Map<String, String> start() {
        opensearch = new OpensearchContainer(DockerImageName.parse("opensearchproject/opensearch:3.1.0"))
                .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
                .withEnv("discovery.type", "single-node")
                // Disable security for easier testing
                .withEnv("plugins.security.disabled", "true");

        opensearch.start();

        return Map.of("quarkus.opensearch.hosts", opensearch.getHttpHostAddress());
    }

    @Override
    public void stop() {
        if (opensearch != null) {
            opensearch.stop();
        }
    }
}
