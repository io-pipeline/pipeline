package io.pipeline.module.echo;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.module.PipeStepProcessorClient;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;


/**
 * Integration test for EchoService with real test data.
 * This test extends the base test class but creates ProtobufTestDataHelper directly
 * since integration tests cannot inject beans.
 */
@QuarkusIntegrationTest
class EchoServiceRealDataIT extends EchoServiceTestBase {

    // Create helper directly - integration tests cannot inject
    private final ProtobufTestDataHelper testDataHelper = new ProtobufTestDataHelper();

    private ManagedChannel channel;
    private PipeStepProcessor echoService;

    @BeforeEach
    void setUp() {
        // Get the test port from Quarkus configuration
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);

        System.out.println("Connecting gRPC client to localhost:" + port);

        // Create a real gRPC channel
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext() // No TLS for local tests
                .build();

        // Create the client using the generated PipeStepProcessorClient
        echoService = new PipeStepProcessorClient("echo", channel, (name, stub) -> stub);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Override
    protected PipeStepProcessor getEchoService() {
        return echoService;
    }

    @Override
    protected ProtobufTestDataHelper getTestDataHelper() {
        return testDataHelper;
    }

    @Test
    void testWithRealData() {
        // Now we can use the gRPC client to test with real data
        Collection<PipeDoc> docs = getTestDataHelper().getSamplePipeDocuments();
        assertThat("Sample documents collection should not be empty", docs, is(not(empty())));

        // Process the first document as a test
        if (!docs.isEmpty()) {
            PipeDoc testDoc = docs.iterator().next();
            System.out.println("Testing with real data document: " + testDoc.getId());

            // Use the base class test method which will use our gRPC client
            testProcessData();
        }
    }
}
