package org.eclipse.cargotracker.ai.aiservices.airag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;

import java.util.ArrayList;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Historical Data Indexer for RAG.
 * 
 * Extracts cargo and handling event data from the H2 database,
 * generates embeddings using Azure OpenAI, and stores them in Qdrant
 * for semantic search.
 * 
 * @author Brian Benz & Ed Burns
 * @since 3.1
 */
@ApplicationScoped
public class AiHistoricalIndexer {
    
    private static final Logger logger = Logger.getLogger(AiHistoricalIndexer.class.getName());
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Inject
    private EmbeddingModel embeddingModel;
    
    @Inject
    private EmbeddingStore<TextSegment> embeddingStore;
    
    private boolean indexed = false;
    
    /**
     * Index historical cargo and handling event data.
     * Called automatically on first query or manually via REST API.
     */
    public synchronized void indexHistoricalData() {
        if (embeddingModel == null || embeddingStore == null) {
            logger.warning("Embedding model or store not available. Skipping historical data indexing.");
            logger.info("To enable RAG features, configure Azure OpenAI and start Qdrant.");
            return;
        }
        
        if (indexed) {
            logger.info("Historical data already indexed. Skipping.");
            return;
        }
        
        try {
            logger.info("🚀 Starting historical cargo and handling event indexing for RAG...");
            
            List<TextSegment> segments = new ArrayList<>();
            
            // Index cargo records
            List<Cargo> allCargo = entityManager.createNamedQuery("Cargo.findAll", Cargo.class)
                    .getResultList();
            
            logger.info("Found " + allCargo.size() + " cargo records to index.");
            
            for (Cargo cargo : allCargo) {
                try {
                    String cargoText = buildCargoDocument(cargo);
                    
                    Metadata metadata = new Metadata();
                    metadata.put("trackingId", cargo.getTrackingId().getIdString());
                    metadata.put("origin", cargo.getOrigin().getName());
                    metadata.put("destination", cargo.getRouteSpecification().getDestination().getName());
                    metadata.put("type", "cargo");
                    
                    segments.add(TextSegment.from(cargoText, metadata));
                } catch (Exception e) {
                    logger.warning("Failed to process cargo " + cargo.getTrackingId() + ": " + e.getMessage());
                }
            }
            
            // Index handling events
            List<HandlingEvent> allEvents = entityManager
                    .createQuery("SELECT e FROM HandlingEvent e", HandlingEvent.class)
                    .getResultList();
            
            logger.info("Found " + allEvents.size() + " handling events to index.");
            
            for (HandlingEvent event : allEvents) {
                try {
                    String eventText = buildHandlingEventDocument(event);
                    
                    Metadata metadata = new Metadata();
                    metadata.put("trackingId", event.getCargo().getTrackingId().getIdString());
                    metadata.put("eventType", event.getType().name());
                    metadata.put("location", event.getLocation().getName());
                    metadata.put("type", "handling_event");
                    
                    segments.add(TextSegment.from(eventText, metadata));
                } catch (Exception e) {
                    logger.warning("Failed to process handling event: " + e.getMessage());
                }
            }
            
            if (segments.isEmpty()) {
                logger.info("No data to index yet. Will index when data is available.");
                indexed = true;
                return;
            }
            
            // Generate embeddings and store in Qdrant
            logger.info("Generating embeddings for " + segments.size() + " documents...");
            embeddingStore.addAll(segments.stream()
                    .map(segment -> embeddingModel.embed(segment).content())
                    .collect(Collectors.toList()), 
                    segments);
            
            logger.info("✅ Successfully indexed " + segments.size() 
                    + " documents to Qdrant (" + allCargo.size() + " cargo + " 
                    + allEvents.size() + " handling events).");
            
            indexed = true;
            
        } catch (Exception e) {
            logger.severe("Failed to index historical data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Build a text document from cargo data for embedding.
     */
    private String buildCargoDocument(Cargo cargo) {
        StringBuilder doc = new StringBuilder();
        
        doc.append("Cargo Tracking ID: ").append(cargo.getTrackingId().getIdString()).append("\n");
        doc.append("Origin: ").append(cargo.getOrigin().getName()).append("\n");
        doc.append("Destination: ").append(cargo.getRouteSpecification().getDestination().getName()).append("\n");
        doc.append("Arrival Deadline: ").append(cargo.getRouteSpecification().getArrivalDeadline()).append("\n");
        
        if (cargo.getItinerary() != null && !cargo.getItinerary().getLegs().isEmpty()) {
            doc.append("Route: ");
            cargo.getItinerary().getLegs().forEach(leg -> {
                doc.append(leg.getLoadLocation().getName())
                   .append(" → ")
                   .append(leg.getUnloadLocation().getName())
                   .append(" via ")
                   .append(leg.getVoyage().getVoyageNumber().getIdString())
                   .append("; ");
            });
            doc.append("\n");
        }
        
        // Add delivery status
        if (cargo.getDelivery() != null) {
            doc.append("Transport Status: ").append(cargo.getDelivery().getTransportStatus()).append("\n");
            doc.append("Routing Status: ").append(cargo.getDelivery().getRoutingStatus()).append("\n");
            
            if (cargo.getDelivery().getCurrentVoyage() != null) {
                doc.append("Current Voyage: ").append(cargo.getDelivery().getCurrentVoyage().getVoyageNumber().getIdString()).append("\n");
            }
            
            if (cargo.getDelivery().getLastKnownLocation() != null) {
                doc.append("Last Known Location: ").append(cargo.getDelivery().getLastKnownLocation().getName()).append("\n");
            }
        }
        
        return doc.toString();
    }
    
    /**
     * Build a text document from a handling event for embedding.
     */
    private String buildHandlingEventDocument(HandlingEvent event) {
        StringBuilder doc = new StringBuilder();
        
        doc.append("Handling Event for Cargo: ").append(event.getCargo().getTrackingId().getIdString()).append("\n");
        doc.append("Event Type: ").append(event.getType().name()).append("\n");
        doc.append("Location: ").append(event.getLocation().getName()).append("\n");
        doc.append("Completed: ").append(event.getCompletionTime()).append("\n");
        doc.append("Registered: ").append(event.getRegistrationTime()).append("\n");
        
        if (event.getVoyage() != null && event.getVoyage().getVoyageNumber() != null
                && !event.getVoyage().getVoyageNumber().getIdString().isEmpty()) {
            doc.append("Voyage: ").append(event.getVoyage().getVoyageNumber().getIdString()).append("\n");
        }
        
        // Add cargo context for richer embeddings
        Cargo cargo = event.getCargo();
        doc.append("Cargo Origin: ").append(cargo.getOrigin().getName()).append("\n");
        doc.append("Cargo Destination: ").append(cargo.getRouteSpecification().getDestination().getName()).append("\n");
        
        return doc.toString();
    }
    
    /**
     * Re-index all historical data.
     * Clears existing points first to prevent duplicates,
     * then re-indexes everything from the database.
     */
    private static final String COLLECTION_NAME = "cargo_tracker_history";

    public void reindex() {
        if (embeddingStore != null) {
            logger.info("Clearing existing Qdrant points before reindex...");
            clearQdrantCollection();
        }
        indexed = false;
        indexHistoricalData();
    }

    /**
     * Delete and recreate the Qdrant collection to clear all points.
     * Uses the Qdrant REST API (HTTP) to avoid CDI proxy and gRPC client issues.
     */
    private void clearQdrantCollection() {
        String host = System.getenv().getOrDefault("QDRANT_HOST", "localhost");
        // REST API runs on port 6333 (one less than gRPC port 6334)
        String restPort = "6333";
        String baseUrl = "http://" + host + ":" + restPort + "/collections/" + COLLECTION_NAME;

        try {
            // DELETE the collection
            HttpURLConnection deleteCon = (HttpURLConnection) URI.create(baseUrl).toURL().openConnection();
            deleteCon.setRequestMethod("DELETE");
            int deleteStatus = deleteCon.getResponseCode();
            deleteCon.disconnect();
            logger.info("DELETE collection response: " + deleteStatus);

            // PUT to recreate with same vector config
            HttpURLConnection createCon = (HttpURLConnection) URI.create(baseUrl).toURL().openConnection();
            createCon.setRequestMethod("PUT");
            createCon.setRequestProperty("Content-Type", "application/json");
            createCon.setDoOutput(true);
            String body = "{\"vectors\":{\"size\":1536,\"distance\":\"Cosine\"}}";
            createCon.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            int createStatus = createCon.getResponseCode();
            createCon.disconnect();
            logger.info("CREATE collection response: " + createStatus);
        } catch (Exception e) {
            logger.warning("Failed to clear Qdrant collection: " + e.getMessage());
        }
    }
    
    /**
     * Check if historical data has been indexed.
     * 
     * @return true if indexed
     */
    public boolean isIndexed() {
        return indexed;
    }
}
