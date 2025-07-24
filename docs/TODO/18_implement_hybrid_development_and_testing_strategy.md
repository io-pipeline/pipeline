## 18. Implement Hybrid Development and Testing Strategy

**Goal:** Implement the hybrid development and testing strategy as outlined in `docs/docker-strategy.md`, using a `compose-devservices.yml` file to manage backing services and leveraging Quarkus profiles to control application behavior.

### Ticket 17.1: Create `compose-devservices.yml`

**Title:** Chore: Create and configure `compose-devservices.yml` for local development

**Description:**
Create a `compose-devservices.yml` file in the root of the project to manage backing services like Consul and Kafka. This file will be used by Quarkus Dev Services to provide a consistent local development environment.

**Tasks:**
1.  Create a new file named `compose-devservices.yml` in the project root.
2.  Add a service definition for Consul, using a specific version (e.g., `1.18.0`) and exposing the necessary ports.
3.  Add a `healthcheck` to the Consul service definition to ensure that Quarkus waits for Consul to be fully ready before starting the application.
4.  Add service definitions for any other backing services that are required for local development (e.g., Kafka, databases).

### Ticket 17.2: Configure Application Profiles

**Title:** Refactor: Configure application profiles to disable `consul-config` in dev and test

**Description:**
Use Quarkus configuration profiles to disable the `quarkus-config-consul` extension in the `dev` and `test` profiles. This will prevent the startup paradox and allow the application to start successfully when using Dev Services.

**Tasks:**
1.  In the `application.properties` file, add the following configuration:
    ```properties
    # Disable consul-config in dev and test modes
    %dev,test.quarkus.consul-config.enabled=false
    ```
2.  Ensure that any configuration that would normally come from Consul is provided locally in the `application.properties` file for the `dev` and `test` profiles.
3.  The production environment will enable the `consul-config` extension by setting the `QUARKUS_CONSUL_CONFIG_ENABLED=true` environment variable.

### Ticket 17.3: Configure Stork for Service Discovery

**Title:** Chore: Configure Stork to use Consul for service discovery

**Description:**
Configure SmallRye Stork to use Consul for service discovery. In the `dev` and `test` profiles, Stork will automatically connect to the Consul instance provided by Dev Services.

**Tasks:**
1.  In the `application.properties` file, add the necessary Stork configuration for each service that needs to be discovered. For example:
    ```properties
    # Configure Stork to use Consul for discovering 'my-other-service'.
    # In dev/test profiles, Quarkus Dev Services will automatically configure
    # the consul-host and consul-port for Stork.
    quarkus.stork.my-other-service.service-discovery.type=consul
    ```
2.  Ensure that the production configuration for Stork points to the correct Consul instance.
