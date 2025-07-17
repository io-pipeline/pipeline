package io.pipeline.data.util;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public abstract class ProtobufTestDataHelperTestBase {

    protected abstract ProtobufTestDataHelper getProtobufTestDataHelper();

    @Test
    void testTikaRequestStreamsLoading() {
        Collection<PipeStream> tikaRequests = getProtobufTestDataHelper().getTikaRequestStreams();
        assertNotNull(tikaRequests);
        assertFalse(tikaRequests.isEmpty());
    }

    @Test
    void testTikaResponseDocumentsLoading() {
        Collection<PipeDoc> tikaResponses = getProtobufTestDataHelper().getTikaResponseDocuments();
        assertNotNull(tikaResponses);
        assertFalse(tikaResponses.isEmpty());
    }

    @Test
    void testChunkerInputDocumentsLoading() {
        Collection<PipeDoc> chunkerInputs = getProtobufTestDataHelper().getChunkerInputDocuments();
        assertNotNull(chunkerInputs);
        assertFalse(chunkerInputs.isEmpty());
    }

    @Test
    void testParserOutputDocumentsLoading() {
        Collection<PipeDoc> parserOutputDocs = getProtobufTestDataHelper().getParserOutputDocs();

        assertNotNull(parserOutputDocs);
        assertFalse(parserOutputDocs.isEmpty());
        parserOutputDocs.forEach(doc -> {
            assertNotNull(doc);
            assertFalse(doc.getId().isEmpty());
            assertTrue(doc.hasBody());
            assertFalse(doc.getBody().isEmpty());
        });
    }

    @Test
    void testPipelineStages() {
        Set<String> stages = getProtobufTestDataHelper().getPipelineStages();

        // Verify we can load documents for each stage
        stages.forEach(stage -> {
            Collection<PipeDoc> stageDocs = getProtobufTestDataHelper().getPipelineGeneratedDocuments(stage);
            assertNotNull(stageDocs, "Documents for stage: " + stage); // May be empty for some stages
        });
    }
}