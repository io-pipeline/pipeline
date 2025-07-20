package io.pipeline.module.chunker.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for form-based chunker endpoints.
 * These endpoints provide form-based inputs for easier testing in Swagger UI.
 */
@QuarkusTest
public class ChunkerFormEndpointTest {

    private static final String BASE_PATH = "/api/chunker/service";

    @Test
    public void testSimpleFormEndpoint() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("text", "This is a test document for form-based chunking. It demonstrates how form inputs work.")
            .formParam("chunkSize", 100)
            .formParam("chunkOverlap", 20)
            .formParam("preserveUrls", true)
            .when().post(BASE_PATH + "/simple-form")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", notNullValue())
            .body("chunks", not(empty()))
            .body("chunks[0].text", notNullValue())
            .body("metadata.totalChunks", greaterThan(0));
    }

    @Test
    public void testAdvancedFormEndpoint() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("documentId", "test-form-doc")
            .formParam("title", "Form Test Document")
            .formParam("body", "This is the body content that will be chunked using the advanced form endpoint.")
            .formParam("sourceField", "body")
            .formParam("chunkSize", 80)
            .formParam("chunkOverlap", 15)
            .formParam("preserveUrls", false)
            .formParam("chunkIdTemplate", "test-form-%s-%s-%d")
            .formParam("streamId", "test-form-stream")
            .formParam("pipeStepName", "test-form-step")
            .when().post(BASE_PATH + "/advanced-form")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", notNullValue())
            .body("chunks", not(empty()))
            .body("chunks[0].id", containsString("test-form"))
            .body("metadata.totalChunks", greaterThan(0));
    }

    @Test
    public void testQuickTestFormEndpoint() {
        String testText = "Quick test for the form-based endpoint. This should be chunked with default settings.";
        
        given()
            .contentType(ContentType.URLENC)
            .formParam("text", testText)
            .when().post(BASE_PATH + "/quick-test-form")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", notNullValue())
            .body("chunks", not(empty()))
            .body("metadata.originalTextLength", equalTo(testText.length()));
    }

    @Test
    public void testFormWithLargeText() {
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            largeText.append("This is paragraph ").append(i + 1)
                    .append(" of a larger document. It contains multiple sentences to test form-based chunking. ");
        }

        given()
            .contentType(ContentType.URLENC)
            .formParam("text", largeText.toString())
            .formParam("chunkSize", 150)
            .formParam("chunkOverlap", 30)
            .formParam("preserveUrls", true)
            .when().post(BASE_PATH + "/simple-form")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", hasSize(greaterThan(1)))
            .body("metadata.totalChunks", greaterThan(1));
    }

    @Test
    public void testAdvancedFormWithTitleSource() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("documentId", "title-test-doc")
            .formParam("title", "This title will be chunked instead of the body content")
            .formParam("body", "This body content should be ignored")
            .formParam("sourceField", "title")
            .formParam("chunkSize", 50)
            .formParam("chunkOverlap", 10)
            .when().post(BASE_PATH + "/advanced-form")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", equalTo(true))
            .body("chunks", not(empty()))
            .body("chunks[0].text", containsString("title"));
    }
}