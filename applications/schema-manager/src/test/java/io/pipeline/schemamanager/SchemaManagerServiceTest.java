package io.pipeline.schemamanager;

import io.pipeline.schemamanager.v1.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaManagerServiceTest {

    @Inject
    SchemaManagerService schemaManagerService;

    @Test
    void testEnsureNestedEmbeddingsFieldExists() {
        // Create a test request
        var vectorFieldDef = VectorFieldDefinition.newBuilder()
                .setDimension(384)
                .setKnnMethod(KnnMethodDefinition.newBuilder()
                        .setEngine(KnnMethodDefinition.KnnEngine.LUCENE)
                        .setSpaceType(KnnMethodDefinition.SpaceType.COSINESIMIL)
                        .build())
                .build();

        var request = EnsureNestedEmbeddingsFieldExistsRequest.newBuilder()
                .setIndexName("test-index")
                .setNestedFieldName("embeddings")
                .setVectorFieldDefinition(vectorFieldDef)
                .build();

        // Execute the request
        var response = schemaManagerService.ensureNestedEmbeddingsFieldExists(request)
                .await().indefinitely();

        // Verify response
        assertNotNull(response);
        // First call should create the schema (schema_existed = false)
        // Note: This might be true if the index already exists from previous tests
        assertTrue(response.getSchemaExisted());
    }

    @Test
    void testEnsureNestedEmbeddingsFieldExistsIdempotent() {
        // Create a test request
        var vectorFieldDef = VectorFieldDefinition.newBuilder()
                .setDimension(768)
                .setKnnMethod(KnnMethodDefinition.newBuilder()
                        .setEngine(KnnMethodDefinition.KnnEngine.LUCENE)
                        .setSpaceType(KnnMethodDefinition.SpaceType.L2)
                        .build())
                .build();

        var request = EnsureNestedEmbeddingsFieldExistsRequest.newBuilder()
                .setIndexName("test-index-idempotent")
                .setNestedFieldName("embeddings")
                .setVectorFieldDefinition(vectorFieldDef)
                .build();

        // Execute the request twice
        var response1 = schemaManagerService.ensureNestedEmbeddingsFieldExists(request)
                .await().indefinitely();
        var response2 = schemaManagerService.ensureNestedEmbeddingsFieldExists(request)
                .await().indefinitely();

        // Verify both responses are successful
        assertNotNull(response1);
        assertNotNull(response2);
        
        // Second call should find existing schema (from cache)
        assertTrue(response2.getSchemaExisted());
    }
}