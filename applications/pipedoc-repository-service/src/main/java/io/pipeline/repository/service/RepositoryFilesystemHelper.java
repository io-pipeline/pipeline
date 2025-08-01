package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.repository.filesystem.CreateNodeRequest;
import io.pipeline.repository.filesystem.Node;
import io.quarkus.grpc.GrpcService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper service to integrate filesystem operations with repository documents.
 * Provides convenient methods for storing different types of pipeline documents.
 */
@ApplicationScoped
public class RepositoryFilesystemHelper {
    
    private static final Logger LOG = Logger.getLogger(RepositoryFilesystemHelper.class);
    
    @Inject
    @GrpcService
    FilesystemServiceImpl filesystemService;
    
    // Common SVG icons for different document types
    private static final String FOLDER_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M10 4H4c-1.11 0-2 .89-2 2v12c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2h-8l-2-2z\"/></svg>";
    private static final String PIPEDOC_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M14,2H6A2,2 0 0,0 4,4V20A2,2 0 0,0 6,22H18A2,2 0 0,0 20,20V8L14,2M13,19H7V17H13V19M17,15H7V13H17V15M17,11H7V9H17V11M13,9V3.5L18.5,9H13Z\"/></svg>";
    private static final String REQUEST_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M4 6h2v2H4zm0 5h2v2H4zm0 5h2v2H4zm16-8V6h-8v2h8zm0 5v-2h-8v2h8zm0 5v-2h-8v2h8z\"/></svg>";
    private static final String RESPONSE_ICON = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M20 6h2v2h-2zm0 5h2v2h-2zm0 5h2v2h-2zM4 8V6h8v2H4zm0 5v-2h8v2H4zm0 5v-2h8v2H4z\"/></svg>";
    
    /**
     * Create a folder in the filesystem
     */
    public Node createFolder(String parentId, String name, Map<String, String> metadata) {
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setParentId(parentId != null ? parentId : "")
            .setName(name)
            .setType(Node.NodeType.FOLDER)
            .putAllMetadata(metadata != null ? metadata : new HashMap<>())
            .build();
        
        Node node = filesystemService.createNode(request).await().indefinitely();
        
        // Update with folder icon
        updateNodeIcon(node.getId(), FOLDER_ICON);
        
        return node;
    }
    
    /**
     * Save a PipeDoc to the filesystem
     */
    public Node savePipeDoc(String parentId, PipeDoc pipeDoc, String moduleName) {
        LOG.debugf("Saving PipeDoc: id=%s, title=%s, body=%s", 
            pipeDoc.getId(), pipeDoc.getTitle(), 
            pipeDoc.getBody() != null ? pipeDoc.getBody().substring(0, Math.min(pipeDoc.getBody().length(), 50)) : "null");
        
        String name = pipeDoc.getTitle() != null && !pipeDoc.getTitle().isEmpty() ? 
            pipeDoc.getTitle() : "Untitled Document";
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("documentId", pipeDoc.getId());
        metadata.put("documentType", pipeDoc.getDocumentType());
        metadata.put("module", moduleName);
        if (pipeDoc.getSourceUri() != null) {
            metadata.put("sourceUri", pipeDoc.getSourceUri());
        }
        
        // Create Any wrapper for PipeDoc
        Any payload = Any.pack(pipeDoc);
        LOG.debugf("Packed payload: typeUrl=%s, size=%d bytes", payload.getTypeUrl(), payload.getValue().size());
        
        CreateNodeRequest request = CreateNodeRequest.newBuilder()
            .setParentId(parentId != null ? parentId : "")
            .setName(name)
            .setType(Node.NodeType.FILE)
            .setPayload(payload)
            .putAllMetadata(metadata)
            .build();
        
        Node node = filesystemService.createNode(request).await().indefinitely();
        
        // Update with additional type info
        updateNodeTypeInfo(node.getId(), PIPEDOC_ICON, "PipeDoc", PipeDoc.class.getName());
        
        return node;
    }
    
    /**
     * Save a ModuleProcessRequest to the filesystem
     */
    public Node saveModuleRequest(String parentId, ModuleProcessRequest request, String moduleName) {
        String name = String.format("%s Request - %s", 
            moduleName, 
            request.getDocument().getTitle() != null ? request.getDocument().getTitle() : "Untitled");
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("module", moduleName);
        metadata.put("pipelineName", request.getMetadata().getPipelineName());
        metadata.put("stepName", request.getMetadata().getPipeStepName());
        metadata.put("streamId", request.getMetadata().getStreamId());
        
        // Create Any wrapper
        Any payload = Any.pack(request);
        
        CreateNodeRequest createRequest = CreateNodeRequest.newBuilder()
            .setParentId(parentId != null ? parentId : "")
            .setName(name)
            .setType(Node.NodeType.FILE)
            .setPayload(payload)
            .putAllMetadata(metadata)
            .build();
        
        Node node = filesystemService.createNode(createRequest).await().indefinitely();
        
        // Update with type info
        updateNodeTypeInfo(node.getId(), REQUEST_ICON, "PipeStepProcessor", 
            ModuleProcessRequest.class.getName());
        
        return node;
    }
    
    /**
     * Save a ModuleProcessResponse to the filesystem
     */
    public Node saveModuleResponse(String parentId, ModuleProcessResponse response, String moduleName) {
        String name = String.format("%s Response - %s", 
            moduleName,
            response.getSuccess() ? "Success" : "Failed");
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("module", moduleName);
        metadata.put("success", String.valueOf(response.getSuccess()));
        if (response.hasOutputDoc()) {
            metadata.put("outputDocId", response.getOutputDoc().getId());
        }
        
        // Create Any wrapper
        Any payload = Any.pack(response);
        
        CreateNodeRequest createRequest = CreateNodeRequest.newBuilder()
            .setParentId(parentId != null ? parentId : "")
            .setName(name)
            .setType(Node.NodeType.FILE)
            .setPayload(payload)
            .putAllMetadata(metadata)
            .build();
        
        Node node = filesystemService.createNode(createRequest).await().indefinitely();
        
        // Update with type info
        updateNodeTypeInfo(node.getId(), RESPONSE_ICON, "PipeStepProcessor", 
            ModuleProcessResponse.class.getName());
        
        return node;
    }
    
    /**
     * Extract a PipeDoc from a filesystem node
     */
    public PipeDoc extractPipeDoc(Node node) throws InvalidProtocolBufferException {
        if (node.getType() != Node.NodeType.FILE || !node.hasPayload()) {
            LOG.debugf("Node %s: type=%s, hasPayload=%s", node.getId(), node.getType(), node.hasPayload());
            return null;
        }
        
        Any payload = node.getPayload();
        LOG.debugf("Payload typeUrl=%s, size=%d bytes", payload.getTypeUrl(), payload.getValue().size());
        
        if (payload.is(PipeDoc.class)) {
            PipeDoc doc = payload.unpack(PipeDoc.class);
            LOG.debugf("Unpacked PipeDoc: id=%s, title=%s, body=%s", 
                doc.getId(), doc.getTitle(), 
                doc.getBody() != null ? doc.getBody().substring(0, Math.min(doc.getBody().length(), 50)) : "null");
            return doc;
        }
        
        // If it's a ModuleProcessRequest, extract the document
        if (payload.is(ModuleProcessRequest.class)) {
            ModuleProcessRequest request = payload.unpack(ModuleProcessRequest.class);
            return request.getDocument();
        }
        
        // If it's a ModuleProcessResponse, extract the output document
        if (payload.is(ModuleProcessResponse.class)) {
            ModuleProcessResponse response = payload.unpack(ModuleProcessResponse.class);
            return response.hasOutputDoc() ? response.getOutputDoc() : null;
        }
        
        return null;
    }
    
    /**
     * Create a standard folder structure for organizing documents
     */
    public void createStandardFolders() {
        try {
            // Create root folders
            createFolder(null, "Seed Data", Map.of("type", "seeds"));
            createFolder(null, "Test Requests", Map.of("type", "requests"));
            createFolder(null, "Test Responses", Map.of("type", "responses"));
            createFolder(null, "Archives", Map.of("type", "archives"));
            
            LOG.info("Created standard folder structure");
        } catch (Exception e) {
            LOG.warn("Some standard folders may already exist", e);
        }
    }
    
    private void updateNodeIcon(String nodeId, String iconSvg) {
        // This would update just the icon field
        // For now, we'll rely on the create method setting it
    }
    
    private void updateNodeTypeInfo(String nodeId, String iconSvg, String serviceType, String payloadType) {
        // This would update the type information fields
        // For now, we'll rely on the create method setting it
    }
}