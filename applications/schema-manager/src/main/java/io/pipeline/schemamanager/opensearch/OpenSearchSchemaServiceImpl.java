package io.pipeline.schemamanager.opensearch;

import io.pipeline.schemamanager.v1.KnnMethodDefinition;
import io.pipeline.schemamanager.v1.VectorFieldDefinition;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.GetMappingResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.mapping.NestedProperty;
import org.opensearch.client.opensearch._types.mapping.KnnVectorProperty;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.json.JsonData;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

@ApplicationScoped
public class OpenSearchSchemaServiceImpl implements OpenSearchSchemaService {

    @Inject
    OpenSearchClient client;

    @Override
    public Uni<Boolean> nestedMappingExists(String indexName, String nestedFieldName) {
        return Uni.createFrom().item(() -> {
            try {
                boolean exists = client.indices().exists(new ExistsRequest.Builder().index(indexName).build()).value();
                if (!exists) return false;
                
                var mapping = client.indices().getMapping(b -> b.index(indexName));
                return mappingContainsNestedField(mapping, nestedFieldName);
            } catch (IOException e) {
                return false;
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private boolean mappingContainsNestedField(GetMappingResponse mapping, String nestedFieldName) {
        return mapping.result().values().stream()
                .anyMatch(indexMapping -> {
                    var properties = indexMapping.mappings().properties();
                    return properties.containsKey(nestedFieldName) && 
                           properties.get(nestedFieldName).isNested();
                });
    }

    @Override
    public Uni<Boolean> createIndexWithNestedMapping(String indexName, String nestedFieldName, VectorFieldDefinition vectorFieldDefinition) {
        return Uni.createFrom().item(() -> {
            try {
                var settings = new IndexSettings.Builder().knn(true).build();
                
                var mapping = new TypeMapping.Builder()
                    .properties(nestedFieldName, Property.of(property -> property
                        .nested(NestedProperty.of(nested -> nested
                            .properties(Map.of(
                                "model_name", Property.of(p -> p.text(TextProperty.of(t -> t))),
                                "vector", Property.of(p -> p.knnVector(createKnnVectorProperty(vectorFieldDefinition)))
                            ))
                        ))
                    ))
                    .build();
                
                var createRequest = new org.opensearch.client.opensearch.indices.CreateIndexRequest.Builder()
                    .index(indexName)
                    .settings(settings)
                    .mappings(mapping)
                    .build();
                
                var response = client.indices().create(createRequest);
                return response.acknowledged();
            } catch (IOException e) {
                return false;
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
    
    private KnnVectorProperty createKnnVectorProperty(VectorFieldDefinition vectorDef) {
        return KnnVectorProperty.of(knn -> {
            knn.dimension(vectorDef.getDimension());
            
            if (vectorDef.hasKnnMethod()) {
                var method = vectorDef.getKnnMethod();
                knn.method(methodDef -> {
                    methodDef.engine(method.getEngine().name().toLowerCase());
                    methodDef.spaceType(mapSpaceType(method.getSpaceType()));
                    
                    if (method.hasParameters()) {
                        var params = method.getParameters();
                        var paramsMap = new HashMap<String, JsonData>();
                        
                        if (params.hasM()) {
                            paramsMap.put("m", JsonData.of(params.getM().getValue()));
                        }
                        if (params.hasEfConstruction()) {
                            paramsMap.put("ef_construction", JsonData.of(params.getEfConstruction().getValue()));
                        }
                        if (params.hasEfSearch()) {
                            paramsMap.put("ef_search", JsonData.of(params.getEfSearch().getValue()));
                        }
                        if (!paramsMap.isEmpty()) {
                            methodDef.parameters(paramsMap);
                        }
                    }
                    
                    return methodDef;
                });
            }
            
            return knn;
        });
    }
    
    private String mapSpaceType(KnnMethodDefinition.SpaceType spaceType) {
        return switch (spaceType) {
            case L2 -> "l2";
            case COSINESIMIL -> "cosinesimil";
            case INNERPRODUCT -> "innerproduct";
            case UNRECOGNIZED -> "cosinesimil";
        };
    }
}
