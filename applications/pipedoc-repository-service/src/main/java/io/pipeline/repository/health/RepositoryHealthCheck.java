package io.pipeline.repository.health;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class RepositoryHealthCheck implements HealthCheck {

    @Inject
    ReactiveRedisDataSource redis;

    @Override
    public HealthCheckResponse call() {
        try {
            // Try to get a key to verify connection
            redis.key(String.class).exists("health:check")
                .await().indefinitely();
            
            return HealthCheckResponse.named("Redis connection")
                    .up()
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("Redis connection")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}