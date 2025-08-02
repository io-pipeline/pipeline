package io.pipeline.schemamanager.opensearch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.hc.core5.http.HttpHost;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import java.net.URISyntaxException;

@ApplicationScoped
public class OpenSearchClientProducer {

    @ConfigProperty(name = "quarkus.opensearch.hosts")
    String openSearchHosts;

    @Produces
    @ApplicationScoped
    public OpenSearchClient openSearchClient() {
        try {
            final HttpHost host = HttpHost.create(openSearchHosts);
            final OpenSearchTransport transport = ApacheHttpClient5TransportBuilder.builder(host).build();
            return new OpenSearchClient(transport);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid OpenSearch host URI: " + openSearchHosts, e);
        }
    }
}
