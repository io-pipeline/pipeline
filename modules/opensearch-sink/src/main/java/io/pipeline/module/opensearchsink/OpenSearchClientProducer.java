package io.pipeline.module.opensearchsink;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;

import javax.net.ssl.SSLContext;

@ApplicationScoped
public class OpenSearchClientProducer {
    
    private static final Logger LOG = Logger.getLogger(OpenSearchClientProducer.class);
    
    @ConfigProperty(name = "opensearch.hosts")
    String hosts;
    
    @ConfigProperty(name = "opensearch.protocol", defaultValue = "https")
    String protocol;
    
    @ConfigProperty(name = "opensearch.username")
    String username;
    
    @ConfigProperty(name = "opensearch.password") 
    String password;
    
    @ConfigProperty(name = "opensearch.ssl-verify", defaultValue = "true")
    boolean sslVerify;
    
    @Produces
    @Singleton
    public OpenSearchClient openSearchClient() {
        try {
            LOG.infof("Creating OpenSearch client for %s://%s", protocol, hosts);
            
            // Parse hosts string (comma-separated list of host:port)
            String[] hostEntries = hosts.split(",");
            HttpHost[] httpHosts = new HttpHost[hostEntries.length];
            
            for (int i = 0; i < hostEntries.length; i++) {
                String hostEntry = hostEntries[i].trim();
                String host;
                int port = 9200; // Default OpenSearch port
                
                if (hostEntry.contains(":")) {
                    String[] parts = hostEntry.split(":");
                    host = parts[0];
                    port = Integer.parseInt(parts[1]);
                } else {
                    host = hostEntry;
                }
                
                httpHosts[i] = new HttpHost(protocol, host, port);
            }
            
            // Create SSL context if needed
            SSLContext sslContext = null;
            if ("https".equals(protocol) && !sslVerify) {
                sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, (chains, authType) -> true)
                    .build();
            }
            
            // Build the transport with proper authentication and SSL settings
            final SSLContext finalSslContext = sslContext;
            var transport = ApacheHttpClient5TransportBuilder.builder(httpHosts)
                .setMapper(new JacksonJsonpMapper())
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    // Set up authentication only if username and password are provided
                    if (username != null && !username.trim().isEmpty() && 
                        password != null && !password.trim().isEmpty()) {
                        LOG.debug("Setting up HTTP Basic authentication");
                        final var credentialsProvider = new BasicCredentialsProvider();
                        for (final var httpHost : httpHosts) {
                            credentialsProvider.setCredentials(
                                new AuthScope(httpHost), 
                                new UsernamePasswordCredentials(username, password.toCharArray())
                            );
                        }
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    } else {
                        LOG.debug("No authentication credentials provided - connecting without auth");
                    }
                    
                    // Configure SSL if needed
                    if (finalSslContext != null) {
                        final var tlsStrategy = ClientTlsStrategyBuilder.create()
                            .setSslContext(finalSslContext)
                            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                            .build();
                        
                        final var connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                            .setTlsStrategy(tlsStrategy)
                            .build();
                        
                        httpClientBuilder.setConnectionManager(connectionManager);
                    }
                    
                    return httpClientBuilder;
                })
                .build();
            
            var client = new OpenSearchClient(transport);
            
            LOG.info("OpenSearch client created successfully");
            return client;
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to create OpenSearch client");
            throw new RuntimeException("Failed to create OpenSearch client", e);
        }
    }
}