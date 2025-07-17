package com.rokkon.testmodule;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.util.ProtobufTestDataHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import com.rokkon.pipeline.testing.util.UnifiedTestProfile;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that ProtobufTestDataHelper loads test data correctly
 * in the testing-harness module context.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
// Temporarily enabling test to see error messages
// @Disabled("This test is disabled by default, since it requires a large amount of memory to run")
class TestDataLoadingTest {

    private static final Logger LOG = Logger.getLogger(TestDataLoadingTest.class);

    @Inject
    ProtobufTestDataHelper testDataHelper;

    @Test
    void testLoadSampleDocuments() {
        Collection<PipeDoc> sampleDocs = testDataHelper.getSamplePipeDocuments();

        assertThat(sampleDocs).isNotEmpty();
        LOG.infof("Loaded %d sample documents", sampleDocs.size());

        // Verify document structure
        PipeDoc firstDoc = sampleDocs.iterator().next();
        assertThat(firstDoc.getId()).isNotEmpty();
        assertThat(firstDoc.getBody()).isNotEmpty();

        // Most sample docs should have titles
        long docsWithTitles = sampleDocs.stream()
                .filter(PipeDoc::hasTitle)
                .count();
        LOG.infof("Documents with titles: %d/%d", docsWithTitles, sampleDocs.size());
    }

    @Test
    void testLoadPipeStreams() {
        Collection<PipeStream> streams = testDataHelper.getPipeStreams();

        assertThat(streams).isNotEmpty();
        LOG.infof("Loaded %d pipe streams", streams.size());

        // Verify stream structure
        PipeStream firstStream = streams.iterator().next();
        assertThat(firstStream.getStreamId()).isNotEmpty();
        assertThat(firstStream.hasDocument()).isTrue();
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
        // Test that document maps work correctly
        var docsMap = testDataHelper.getSamplePipeDocumentsMap();
        assertThat(docsMap).isNotEmpty();

        // Pick a document and verify we can retrieve it by ID
        String docId = docsMap.keySet().iterator().next();
        PipeDoc doc = docsMap.get(docId);

        assertThat(doc).isNotNull();
        assertThat(doc.getId()).isEqualTo(docId);
    }

    @Test
    void testOrderedDocuments() {
        // Test ordered document retrieval
        var orderedDocs = testDataHelper.getOrderedSamplePipeDocuments();
        assertThat(orderedDocs).isNotEmpty();

        // Verify ordering is consistent
        if (orderedDocs.size() > 1) {
            String firstId = orderedDocs.get(0).getId();
            String secondId = orderedDocs.get(1).getId();

            // IDs should be consistently ordered
            assertThat(firstId.compareTo(secondId)).isLessThan(0);
        }
    }

    @Test
    void testGetDocumentByIndex() {
        var orderedDocs = testDataHelper.getOrderedSamplePipeDocuments();

        if (orderedDocs.size() > 5) {
            // Test retrieving specific documents by index
            PipeDoc doc0 = testDataHelper.getSamplePipeDocByIndex(0);
            PipeDoc doc5 = testDataHelper.getSamplePipeDocByIndex(5);

            assertThat(doc0).isNotNull();
            assertThat(doc5).isNotNull();
            assertThat(doc0.getId()).isNotEqualTo(doc5.getId());
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
        // Verify that collections and maps have consistent data
        var docs = testDataHelper.getSamplePipeDocuments();
        var docsMap = testDataHelper.getSamplePipeDocumentsMap();

        assertThat(docsMap).hasSameSizeAs(docs);

        // All documents in collection should be in map
        for (PipeDoc doc : docs) {
            assertThat(docsMap).containsKey(doc.getId());
            assertThat(docsMap.get(doc.getId())).isEqualTo(doc);
        }
    }
}
