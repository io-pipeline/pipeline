package io.pipeline.repository.health;

import io.pipeline.repository.entity.StoredPipeDocEntity;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class RepositoryHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        try {
            // Try to count documents to verify MongoDB connection
            long count = StoredPipeDocEntity.count();
            return HealthCheckResponse.named("MongoDB connection")
                    .up()
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("MongoDB connection")
                    .down()
                    .build();
        }
    }
}