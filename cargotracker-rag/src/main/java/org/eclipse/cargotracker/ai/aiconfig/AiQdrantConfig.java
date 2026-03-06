package org.eclipse.cargotracker.ai.aiconfig;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.logging.Logger;
import java.util.concurrent.ExecutionException;

/**
 * AI Configuration for Qdrant Vector Database.
 * 
 * This class provides CDI producers for Qdrant embedding store
 * used for RAG (Retrieval-Augmented Generation) functionality.
 * 
 * @author Brian Benz & Ed Burns
 * @since 3.1
 */
@ApplicationScoped
public class AiQdrantConfig {
    
    private static final Logger logger = Logger.getLogger(AiQdrantConfig.class.getName());
    
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6334;  // gRPC port (6333 is HTTP REST API)
    private static final String COLLECTION_NAME = "cargo_tracker_history";
    
    /**
     * Produces an EmbeddingStore backed by Qdrant.
     * 
     * @return EmbeddingStore instance
     */
    @Produces
    @ApplicationScoped
    public EmbeddingStore<TextSegment> createEmbeddingStore() {
        String host = getConfigValue("QDRANT_HOST", "qdrant.host", DEFAULT_HOST);
        int port = getConfigValueInt("QDRANT_PORT", "qdrant.port", DEFAULT_PORT);
        
        logger.info("Initializing Qdrant Embedding Store - Host: " + host + ", Port: " + port);
        
        try {
            QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(host, port, false).build()
            );
            
            // Create collection if it doesn't exist
            // text-embedding-ada-002 produces 1536-dimensional vectors
            createCollectionIfNotExists(client, COLLECTION_NAME, 1536);
            
            return QdrantEmbeddingStore.builder()
                    .client(client)
                    .collectionName(COLLECTION_NAME)
                    .build();
                    
        } catch (Exception e) {
            logger.severe("Failed to initialize Qdrant: " + e.getMessage());
            logger.warning("AI RAG features will not work without Qdrant. Run ./ai-setup.sh to start Qdrant.");
            return null;
        }
    }
    
    /**
     * Creates a Qdrant collection if it doesn't already exist.
     * 
     * @param client Qdrant client
     * @param collectionName Name of the collection
     * @param vectorSize Dimension of the embedding vectors (1536 for text-embedding-ada-002)
     */
    private void createCollectionIfNotExists(QdrantClient client, String collectionName, int vectorSize) {
        try {
            logger.info("Attempting to create Qdrant collection: " + collectionName);
            
            VectorParams vectorParams = VectorParams.newBuilder()
                    .setSize(vectorSize)
                    .setDistance(Distance.Cosine)
                    .build();
            
            // Try to create collection - will fail if already exists
            client.createCollectionAsync(collectionName, vectorParams).get();
            logger.info("✅ Successfully created collection: " + collectionName);
            
        } catch (ExecutionException e) {
            // Check if error is "already exists" - that's OK
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("already exists")) {
                logger.info("✅ Collection already exists: " + collectionName);
            } else {
                logger.severe("❌ Failed to create collection: " + errorMsg);
                e.printStackTrace();
            }
        } catch (InterruptedException e) {
            logger.warning("⚠️ Interrupted while creating collection: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.severe("❌ Unexpected error creating collection: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Gets string configuration value from environment variable or system property.
     * 
     * @param envVar Environment variable name
     * @param sysProp System property name
     * @param defaultValue Default value if not found
     * @return Configuration value
     */
    private String getConfigValue(String envVar, String sysProp, String defaultValue) {
        String value = System.getenv(envVar);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(sysProp);
        }
        if (value == null || value.isEmpty()) {
            value = defaultValue;
        }
        return value;
    }
    
    /**
     * Gets integer configuration value from environment variable or system property.
     * 
     * @param envVar Environment variable name
     * @param sysProp System property name
     * @param defaultValue Default value if not found
     * @return Configuration value
     */
    private int getConfigValueInt(String envVar, String sysProp, int defaultValue) {
        String value = getConfigValue(envVar, sysProp, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warning("Invalid integer value for " + envVar + ": " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }
}
