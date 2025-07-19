package io.pipeline.module.testharness;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test to verify that ProtobufTestDataHelper loads test data correctly
 * in the testing-harness module context.
 */
@QuarkusTest
// Temporarily enabling test to see error messages
// @Disabled("This test is disabled by default, since it requires a large amount of memory to run")
class TestDataLoadingTest {

    private static final Logger LOG = Logger.getLogger(TestDataLoadingTest.class);

    @Inject
    ProtobufTestDataHelper testDataHelper;

    @Test
    void testLoadSampleDocuments() {
        // Given
        LOG.debug("Testing sample document loading from ProtobufTestDataHelper");
        
        // When
        Collection<PipeDoc> sampleDocs = testDataHelper.getSamplePipeDocuments();

        // Then - Convert AssertJ to Hamcrest with detailed descriptions
        // AssertJ: assertThat(sampleDocs).isNotEmpty() -> Hamcrest: assertThat("Sample documents should not be empty", sampleDocs, is(not(empty())))
        assertThat("Sample documents should not be empty", sampleDocs, is(not(empty())));
        
        LOG.infof("Loaded %d sample documents", sampleDocs.size());

        // Verify document structure
        PipeDoc firstDoc = sampleDocs.iterator().next();
        
        // AssertJ: assertThat(firstDoc.getId()).isNotEmpty() -> Hamcrest: assertThat("Document ID should not be empty", firstDoc.getId(), is(not(emptyString())))
        assertThat("Document ID should not be empty", firstDoc.getId(), is(not(emptyString())));
        
        // AssertJ: assertThat(firstDoc.getBody()).isNotEmpty() -> Hamcrest: assertThat("Document body should not be empty", firstDoc.getBody(), is(not(emptyString())))
        assertThat("Document body should not be empty", firstDoc.getBody(), is(not(emptyString())));

        // Most sample docs should have titles
        long docsWithTitles = sampleDocs.stream()
                .filter(PipeDoc::hasTitle)
                .count();
        LOG.infof("Documents with titles: %d/%d, first doc ID: %s", docsWithTitles, sampleDocs.size(), firstDoc.getId());
    }

    @Test
    void testLoadPipeStreams() {
        // Given
        LOG.debug("Testing pipe stream loading from ProtobufTestDataHelper");
        
        // When
        Collection<PipeStream> streams = testDataHelper.getPipeStreams();

        // Then - Convert AssertJ to Hamcrest with detailed descriptions
        // AssertJ: assertThat(streams).isNotEmpty() -> Hamcrest: assertThat("Pipe streams should not be empty", streams, is(not(empty())))
        assertThat("Pipe streams should not be empty", streams, is(not(empty())));
        
        LOG.infof("Loaded %d pipe streams", streams.size());

        // Verify stream structure
        PipeStream firstStream = streams.iterator().next();
        
        // AssertJ: assertThat(firstStream.getStreamId()).isNotEmpty() -> Hamcrest: assertThat("Stream ID should not be empty", firstStream.getStreamId(), is(not(emptyString())))
        assertThat("Stream ID should not be empty", firstStream.getStreamId(), is(not(emptyString())));
        
        // AssertJ: assertThat(firstStream.hasDocument()).isTrue() -> Hamcrest: assertThat("Stream should have a document", firstStream.hasDocument(), is(true))
        assertThat("Stream should have a document", firstStream.hasDocument(), is(true));
        
        LOG.debugf("First stream ID: %s, has document: %s", firstStream.getStreamId(), firstStream.hasDocument());
    }

    @Test
    void testLoadTikaDocuments() {
        Collection<PipeDoc> tikaDocs = testDataHelper.getTikaPipeDocuments();

        if (!tikaDocs.isEmpty()) {
            LOG.infof("Loaded %d Tika documents", tikaDocs.size());

            // Tika documents often have extracted metadata
            PipeDoc tikaDoc = tikaDocs.iterator().next();
            if (tikaDoc.hasCustomData()) {
                LOG.infof("Tika document has %d custom data fields", 
                        tikaDoc.getCustomData().getFieldsCount());
            }
        } else {
            LOG.info("No Tika documents available in test data");
        }
    }

    @Test
    void testLoadChunkerDocuments() {
        Collection<PipeDoc> chunkerDocs = testDataHelper.getChunkerPipeDocuments();

        if (!chunkerDocs.isEmpty()) {
            LOG.infof("Loaded %d chunker documents", chunkerDocs.size());

            // Chunker documents are usually chunks of larger documents
            PipeDoc chunk = chunkerDocs.iterator().next();
            LOG.infof("Chunk ID: %s, Body length: %d", 
                    chunk.getId(), chunk.getBody().length());
        } else {
            LOG.info("No chunker documents available in test data");
        }
    }

    @Test
    void testDocumentMaps() {
        // Given
        LOG.debug("Testing document map functionality for ID-based retrieval");
        
        // When
        var docsMap = testDataHelper.getSamplePipeDocumentsMap();
        
        // Then - Convert AssertJ to Hamcrest with detailed descriptions
        // AssertJ: assertThat(docsMap).isNotEmpty() -> Hamcrest: assertThat("Document map should not be empty", docsMap.entrySet(), is(not(empty())))
        assertThat("Document map should not be empty", docsMap.entrySet(), is(not(empty())));

        // Pick a document and verify we can retrieve it by ID
        String docId = docsMap.keySet().iterator().next();
        PipeDoc doc = docsMap.get(docId);
        
        LOG.debugf("Testing document retrieval by ID: %s", docId);

        // AssertJ: assertThat(doc).isNotNull() -> Hamcrest: assertThat("Retrieved document should not be null", doc, is(notNullValue()))
        assertThat("Retrieved document should not be null", doc, is(notNullValue()));
        
        // AssertJ: assertThat(doc.getId()).isEqualTo(docId) -> Hamcrest: assertThat("Document ID should match retrieval key", doc.getId(), is(docId))
        assertThat("Document ID should match retrieval key", doc.getId(), is(docId));
        
        LOG.debugf("Document map test completed - map size: %d, retrieved doc ID: %s", docsMap.size(), doc.getId());
    }

    @Test
    void testOrderedDocuments() {
        // Given
        LOG.debug("Testing ordered document retrieval for consistent ordering");
        
        // When
        var orderedDocs = testDataHelper.getOrderedSamplePipeDocuments();
        
        // Then - Convert AssertJ to Hamcrest with detailed descriptions
        // AssertJ: assertThat(orderedDocs).isNotEmpty() -> Hamcrest: assertThat("Ordered documents should not be empty", orderedDocs, is(not(empty())))
        assertThat("Ordered documents should not be empty", orderedDocs, is(not(empty())));

        // Verify ordering is consistent
        if (orderedDocs.size() > 1) {
            String firstId = orderedDocs.get(0).getId();
            String secondId = orderedDocs.get(1).getId();
            
            LOG.debugf("Comparing document order - first ID: %s, second ID: %s", firstId, secondId);

            // IDs should be consistently ordered
            // AssertJ: assertThat(firstId.compareTo(secondId)).isLessThan(0) -> Hamcrest: assertThat("First document ID should be lexicographically before second", firstId.compareTo(secondId), is(lessThan(0)))
            assertThat("First document ID should be lexicographically before second", firstId.compareTo(secondId), is(lessThan(0)));
        }
        
        LOG.debugf("Ordered documents test completed - total documents: %d", orderedDocs.size());
    }

    @Test
    void testGetDocumentByIndex() {
        // Given
        LOG.debug("Testing document retrieval by specific index");
        
        // When
        var orderedDocs = testDataHelper.getOrderedSamplePipeDocuments();

        if (orderedDocs.size() > 5) {
            LOG.debug("Testing document retrieval at indices 0 and 5");
            
            // Test retrieving specific documents by index
            PipeDoc doc0 = testDataHelper.getSamplePipeDocByIndex(0);
            PipeDoc doc5 = testDataHelper.getSamplePipeDocByIndex(5);

            // Then - Convert AssertJ to Hamcrest with detailed descriptions
            // AssertJ: assertThat(doc0).isNotNull() -> Hamcrest: assertThat("Document at index 0 should not be null", doc0, is(notNullValue()))
            assertThat("Document at index 0 should not be null", doc0, is(notNullValue()));
            
            // AssertJ: assertThat(doc5).isNotNull() -> Hamcrest: assertThat("Document at index 5 should not be null", doc5, is(notNullValue()))
            assertThat("Document at index 5 should not be null", doc5, is(notNullValue()));
            
            // AssertJ: assertThat(doc0.getId()).isNotEqualTo(doc5.getId()) -> Hamcrest: assertThat("Documents at different indices should have different IDs", doc0.getId(), is(not(equalTo(doc5.getId()))))
            assertThat("Documents at different indices should have different IDs", doc0.getId(), is(not(equalTo(doc5.getId()))));
            
            LOG.debugf("Document by index test completed - doc0 ID: %s, doc5 ID: %s", doc0.getId(), doc5.getId());
        } else {
            LOG.debugf("Skipping index test - only %d documents available (need > 5)", orderedDocs.size());
        }
    }

    @Test
    void testPipelineGeneratedDocuments() {
        // Test loading pipeline-generated documents from different stages
        var stages = testDataHelper.getPipelineStages();

        if (!stages.isEmpty()) {
            LOG.infof("Found %d pipeline stages: %s", stages.size(), stages);

            for (String stage : stages) {
                Collection<PipeDoc> stageDocs = testDataHelper.getPipelineGeneratedDocuments(stage);
                LOG.infof("Stage '%s' has %d documents", stage, stageDocs.size());
            }
        } else {
            LOG.info("No pipeline-generated documents available");
        }
    }

    @Test
    void testVariousDocumentTypes() {
        // Test loading various specialized document types

        // Tika request/response documents
        var tikaRequests = testDataHelper.getTikaRequestDocuments();
        var tikaResponses = testDataHelper.getTikaResponseDocuments();
        LOG.infof("Tika requests: %d, responses: %d", 
                tikaRequests.size(), tikaResponses.size());

        // Chunker input/output
        var chunkerInput = testDataHelper.getChunkerInputDocuments();
        var chunkerOutput = testDataHelper.getChunkerOutputStreams();
        LOG.infof("Chunker input: %d docs, output: %d streams", 
                chunkerInput.size(), chunkerOutput.size());

        // Embedder documents
        var embedderInput = testDataHelper.getEmbedderInputDocuments();
        var embedderOutput = testDataHelper.getEmbedderOutputDocuments();
        LOG.infof("Embedder input: %d, output: %d", 
                embedderInput.size(), embedderOutput.size());
    }

    @Test
    void testDataConsistency() {
        // Given
        LOG.debug("Testing data consistency between collections and maps");
        
        // When
        var docs = testDataHelper.getSamplePipeDocuments();
        var docsMap = testDataHelper.getSamplePipeDocumentsMap();

        // Then - Convert AssertJ to Hamcrest with detailed descriptions
        // AssertJ: assertThat(docsMap).hasSameSizeAs(docs) -> Hamcrest: assertThat("Document map should have same size as collection", docsMap.size(), is(docs.size()))
        assertThat("Document map should have same size as collection", docsMap.size(), is(docs.size()));
        
        LOG.debugf("Verifying consistency - collection size: %d, map size: %d", docs.size(), docsMap.size());

        // All documents in collection should be in map
        for (PipeDoc doc : docs) {
            // AssertJ: assertThat(docsMap).containsKey(doc.getId()) -> Hamcrest: assertThat("Document map should contain document ID", docsMap, hasKey(doc.getId()))
            assertThat("Document map should contain document ID", docsMap, hasKey(doc.getId()));
            
            // AssertJ: assertThat(docsMap.get(doc.getId())).isEqualTo(doc) -> Hamcrest: assertThat("Document from map should equal document from collection", docsMap.get(doc.getId()), is(equalTo(doc)))
            assertThat("Document from map should equal document from collection", docsMap.get(doc.getId()), is(equalTo(doc)));
        }
        
        LOG.debugf("Data consistency test completed - all %d documents verified", docs.size());
    }
}
