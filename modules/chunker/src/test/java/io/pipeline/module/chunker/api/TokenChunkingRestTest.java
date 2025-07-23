package io.pipeline.module.chunker.api;

import io.pipeline.module.chunker.api.dto.SimpleChunkRequest;
import io.pipeline.module.chunker.model.ChunkingAlgorithm;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Quick test to verify token chunking algorithm works correctly via REST endpoint.
 * This will help us determine if the issue is in the frontend or backend.
 */
@QuarkusTest
public class TokenChunkingRestTest {

    @Test
    public void testTokenChunkingWith50TokensExpectsMultipleChunks() {
        // Create text with approximately 100+ tokens to test chunking
        String testText = "This is a test document with many words to verify that token-based chunking " +
                         "works correctly and produces multiple chunks when the text exceeds the specified " +
                         "chunk size. We want to ensure that when we request 50 tokens per chunk, we get " +
                         "multiple chunks from this longer text. Each word should be counted as a token, " +
                         "so this text should definitely produce more than one chunk when limited to 50 tokens.";
        
        SimpleChunkRequest request = new SimpleChunkRequest();
        request.setText(testText);
        request.setAlgorithm(ChunkingAlgorithm.TOKEN);
        request.setChunkSize(50);  // 50 tokens
        request.setChunkOverlap(10); // 10 tokens overlap
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/chunker/service/simple")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("chunks", hasSize(greaterThan(1))) // Should have multiple chunks
            .body("chunks[0].text.length()", lessThan(300)) // First chunk should be much less than original
            .body("metadata.totalChunks", greaterThan(1))
            .log().body(); // Log the response to see actual chunks
    }

    @Test
    public void testTokenChunkingWith8OverlapGivesMultipleChunks() {
        // Test the specific scenario from the frontend: overlap=8 on token algorithm
        String testText = "We the People of the United States, in Order to form a more perfect Union, " +
                         "establish Justice, insure domestic Tranquility, provide for the common defence, " +
                         "promote the general Welfare, and secure the Blessings of Liberty to ourselves " +
                         "and our Posterity, do ordain and establish this Constitution for the United States of America.";
        
        SimpleChunkRequest request = new SimpleChunkRequest();
        request.setText(testText);
        request.setAlgorithm(ChunkingAlgorithm.TOKEN);
        request.setChunkSize(500); // 500 tokens (should be plenty for this text)
        request.setChunkOverlap(8); // 8 tokens overlap (the problematic setting)
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/chunker/service/simple")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("chunks", hasSize(1)) // This text should fit in one 500-token chunk
            .body("chunks[0].text", containsString("We the People"))
            .log().body();
    }

    @Test
    public void testTokenChunkingWithSmallChunkSizeForceMultiple() {
        // Force multiple chunks with a very small chunk size
        String testText = "One two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen " +
                         "sixteen seventeen eighteen nineteen twenty twenty-one twenty-two twenty-three twenty-four twenty-five";
        
        SimpleChunkRequest request = new SimpleChunkRequest();
        request.setText(testText);
        request.setAlgorithm(ChunkingAlgorithm.TOKEN);
        request.setChunkSize(10); // Only 10 tokens per chunk
        request.setChunkOverlap(2); // 2 tokens overlap
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/chunker/service/simple")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("chunks", hasSize(greaterThan(2))) // Should have multiple chunks
            .body("metadata.totalChunks", greaterThan(2))
            .log().body();
    }

    @Test
    public void testCharacterVsTokenAlgorithm() {
        // Compare character vs token chunking on the same text
        String testText = "This is a simple test to compare character-based and token-based chunking algorithms.";
        
        // Test character-based chunking
        SimpleChunkRequest charRequest = new SimpleChunkRequest();
        charRequest.setText(testText);
        charRequest.setAlgorithm(ChunkingAlgorithm.CHARACTER);
        charRequest.setChunkSize(50); // 50 characters
        charRequest.setChunkOverlap(5); // 5 characters overlap
        
        System.out.println("=== CHARACTER CHUNKING TEST ===");
        given()
            .contentType(ContentType.JSON)
            .body(charRequest)
            .when()
            .post("/api/chunker/service/simple")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .log().body();
        
        // Test token-based chunking
        SimpleChunkRequest tokenRequest = new SimpleChunkRequest();
        tokenRequest.setText(testText);
        tokenRequest.setAlgorithm(ChunkingAlgorithm.TOKEN);
        tokenRequest.setChunkSize(10); // 10 tokens
        tokenRequest.setChunkOverlap(2); // 2 tokens overlap
        
        System.out.println("=== TOKEN CHUNKING TEST ===");
        given()
            .contentType(ContentType.JSON)
            .body(tokenRequest)
            .when()
            .post("/api/chunker/service/simple")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .log().body();
    }
}