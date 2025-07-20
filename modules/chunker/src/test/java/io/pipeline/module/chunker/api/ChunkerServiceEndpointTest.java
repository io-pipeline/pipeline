package io.pipeline.module.chunker.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Comprehensive tests for ChunkerServiceEndpoint REST API.
 * Tests all reactive endpoints with proper Mutiny integration.
 */
@QuarkusTest
public class ChunkerServiceEndpointTest {

    private static final String BASE_PATH = "/api/chunker/service";

    @Test
    public void testHealthEndpoint() {
        given()
            .when().get(BASE_PATH + "/health")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", equalTo("healthy"))
            .body("version", equalTo("1.0.0"))
            .body("models", notNullValue())
            .body("models.sentence", notNullValue())
            .body("models.tokenizer", notNullValue());
    }

    @Test
    public void testConfigEndpoint() {
        given()
            .when().get(BASE_PATH + "/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("schema", notNullValue())
            .body("schema.type", equalTo("object"))
            .body("schema.properties", notNullValue())
            .body("defaults", notNullValue())
            .body("defaults.sourceField", equalTo("body"))
            .body("defaults.chunkSize", equalTo(500))
            .body("defaults.chunkOverlap", equalTo(50))
            .body("defaults.preserveUrls", equalTo(true))
            .body("limits", notNullValue())
            .body("limits.maxChunkSize", equalTo(10000))
            .body("limits.maxTextLength", equalTo(40000000))
            .body("limits.maxChunkOverlap", equalTo(5000));
    }

    @Test
    public void testSimpleChunkingEndpoint() {
        String testText = "This is a test document for chunking. " +
                         "It contains multiple sentences to demonstrate the chunking functionality. " +
                         "Each chunk should contain a reasonable amount of text.";

        String requestBody = """
            {
                "text": "%s",
                "chunkSize": 100,
                "chunkOverlap": 20,
                "preserveUrls": true
            }
            """.formatted(testText);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/simple")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", notNullValue())
            .body("chunks", not(empty()))
            .body("chunks[0].id", notNullValue())
            .body("chunks[0].text", notNullValue())
            .body("chunks[0].startOffset", greaterThanOrEqualTo(0))
            .body("chunks[0].endOffset", greaterThan(0))
            .body("metadata", notNullValue())
            .body("metadata.totalChunks", greaterThan(0))
            .body("metadata.processingTimeMs", greaterThanOrEqualTo(0))
            .body("metadata.originalTextLength", equalTo(testText.length()))
            .body("metadata.tokenizerUsed", notNullValue())
            .body("metadata.sentenceDetectorUsed", notNullValue());
    }

    @Test
    public void testSimpleChunkingWithEmptyText() {
        String requestBody = """
            {
                "text": "",
                "chunkSize": 100,
                "chunkOverlap": 20,
                "preserveUrls": true
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/simple")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("success", equalTo(false))
            .body("error", containsString("Text cannot be null or empty"));
    }

    @Test
    public void testSimpleChunkingWithNullText() {
        String requestBody = """
            {
                "text": null,
                "chunkSize": 100,
                "chunkOverlap": 20,
                "preserveUrls": true
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/simple")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("success", equalTo(false))
            .body("error", containsString("Text cannot be null or empty"));
    }

    @Test
    public void testAdvancedChunkingEndpoint() {
        String requestBody = """
            {
                "document": {
                    "id": "test-doc-advanced",
                    "title": "Test Advanced Document",
                    "body": "This is a more complex document for advanced chunking. It demonstrates the full functionality with custom configuration options.",
                    "metadata": {
                        "source": "test",
                        "type": "advanced"
                    }
                },
                "options": {
                    "sourceField": "body",
                    "chunkSize": 80,
                    "chunkOverlap": 15,
                    "preserveUrls": false,
                    "chunkIdTemplate": "advanced-%s-%s-%d"
                },
                "metadata": {
                    "streamId": "test-stream-advanced",
                    "pipeStepName": "test-chunker-advanced"
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/advanced")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", notNullValue())
            .body("chunks", not(empty()))
            .body("chunks[0].id", containsString("advanced"))
            .body("chunks[0].text", notNullValue())
            .body("chunks[0].startOffset", greaterThanOrEqualTo(0))
            .body("chunks[0].endOffset", greaterThan(0))
            .body("metadata", notNullValue())
            .body("metadata.totalChunks", greaterThan(0))
            .body("metadata.processingTimeMs", greaterThanOrEqualTo(0))
            .body("metadata.originalTextLength", greaterThan(0));
    }

    @Test
    public void testAdvancedChunkingWithNullDocument() {
        String requestBody = """
            {
                "document": null,
                "options": {
                    "sourceField": "body",
                    "chunkSize": 100,
                    "chunkOverlap": 20
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/advanced")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("success", equalTo(false))
            .body("error", containsString("Document cannot be null"));
    }

    @Test
    public void testAdvancedChunkingWithNullOptions() {
        String requestBody = """
            {
                "document": {
                    "id": "test-doc",
                    "body": "Test document body"
                },
                "options": null
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/advanced")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("success", equalTo(false))
            .body("error", containsString("Options cannot be null"));
    }

    @Test
    public void testAdvancedChunkingWithTitleSourceField() {
        String requestBody = """
            {
                "document": {
                    "id": "test-doc-title",
                    "title": "This is the title to be chunked instead of the body",
                    "body": "This body should not be chunked"
                },
                "options": {
                    "sourceField": "title",
                    "chunkSize": 50,
                    "chunkOverlap": 10,
                    "preserveUrls": false
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/advanced")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", notNullValue())
            .body("chunks", not(empty()))
            .body("chunks[0].text", containsString("title"));
    }

    @Test
    public void testTestEndpointWithPlainText() {
        String testText = "Simple plain text for testing the quick endpoint.";

        given()
            .contentType(ContentType.TEXT)
            .body(testText)
            .when().post(BASE_PATH + "/test")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", notNullValue())
            .body("chunks", not(empty()))
            .body("metadata", notNullValue())
            .body("metadata.originalTextLength", equalTo(testText.length()));
    }

    @Test
    public void testTestEndpointWithEmptyText() {
        given()
            .contentType(ContentType.TEXT)
            .body("")
            .when().post(BASE_PATH + "/test")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("success", equalTo(false))
            .body("error", containsString("Text cannot be null or empty"));
    }

    @Test
    public void testChunkingWithUrlPreservation() {
        String textWithUrls = "Visit our website at https://example.com for more information. " +
                             "You can also check https://docs.example.com/api for API documentation.";

        String requestBody = """
            {
                "text": "%s",
                "chunkSize": 100,
                "chunkOverlap": 20,
                "preserveUrls": true
            }
            """.formatted(textWithUrls);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/simple")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", notNullValue())
            .body("chunks", not(empty()))
            // URLs should be preserved in the chunks
            .body("chunks.text", hasItem(containsString("https://example.com")));
    }

    @Test
    public void testChunkingWithLargeText() {
        // Create a larger text to ensure multiple chunks
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            largeText.append("This is sentence ").append(i + 1)
                    .append(" in a larger document used for testing chunking with multiple chunks. ");
        }

        String requestBody = """
            {
                "text": "%s",
                "chunkSize": 150,
                "chunkOverlap": 30,
                "preserveUrls": true
            }
            """.formatted(largeText.toString());

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/simple")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", notNullValue())
            .body("chunks", not(empty()))
            .body("chunks", hasSize(greaterThan(1))) // Should create multiple chunks
            .body("metadata.totalChunks", greaterThan(1));
    }

    @Test
    public void testChunkingPerformance() {
        String mediumText = "Performance testing document. ".repeat(100);

        String requestBody = """
            {
                "text": "%s",
                "chunkSize": 200,
                "chunkOverlap": 40,
                "preserveUrls": true
            }
            """.formatted(mediumText);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when().post(BASE_PATH + "/simple")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("metadata.processingTimeMs", lessThan(5000)) // Should complete in under 5 seconds
            .body("metadata.totalChunks", greaterThan(0));
    }
}