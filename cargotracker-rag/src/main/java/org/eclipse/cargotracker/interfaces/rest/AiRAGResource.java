package org.eclipse.cargotracker.interfaces.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.cargotracker.ai.aiservices.airag.AiRAGQueryService;
import org.eclipse.cargotracker.ai.aiservices.airag.AiRAGResponse;
import org.eclipse.cargotracker.ai.aiservices.airag.AiHistoricalIndexer;

import java.util.Map;
import java.util.logging.Logger;

/**
 * REST API for RAG (Retrieval-Augmented Generation) queries.
 * 
 * Provides endpoints for:
 * - Querying historical cargo data with natural language
 * - Checking RAG service availability
 * - Re-indexing historical data
 * 
 * @author Brian Benz & Ed Burns
 * @since 3.1
 */
@Path("/ai/rag")
@RequestScoped
public class AiRAGResource {
    
    private static final Logger logger = Logger.getLogger(AiRAGResource.class.getName());
    
    @Inject
    private AiRAGQueryService ragQueryService;
    
    @Inject
    private AiHistoricalIndexer historicalIndexer;
    
    /**
     * Query historical cargo data using RAG.
     * 
     * Example request:
     * POST /api/ai/rag/query
     * {
     *   "query": "What cargo shipments went from Hong Kong to Stockholm?"
     * }
     * 
     * @param request Query request containing the question
     * @return RAG response with answer and sources
     */
    @POST
    @Path("/query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response query(Map<String, String> request) {
        try {
            String query = request.get("query");
            
            if (query == null || query.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Query parameter is required"))
                        .build();
            }
            
            logger.info("Received RAG query: " + query);
            
            AiRAGResponse ragResponse = ragQueryService.query(query);
            
            return Response.ok(ragResponse).build();
            
        } catch (Exception e) {
            logger.severe("Error processing RAG query: " + e.getMessage());
            e.printStackTrace();
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to process query: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Check if RAG service is available.
     * 
     * GET /api/ai/rag/status
     * 
     * @return Service status
     */
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        boolean available = ragQueryService.isAvailable();
        boolean indexed = historicalIndexer.isIndexed();
        
        return Response.ok(Map.of(
            "available", available,
            "indexed", indexed,
            "message", available ? 
                (indexed ? "RAG service is ready" : "RAG service is indexing data") :
                "RAG service is not configured. Configure Azure OpenAI and Qdrant."
        )).build();
    }
    
    /**
     * Trigger re-indexing of historical data.
     * 
     * POST /api/ai/rag/reindex
     * 
     * @return Reindex status
     */
    @POST
    @Path("/reindex")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reindex() {
        try {
            logger.info("Manual reindex triggered");
            historicalIndexer.reindex();
            
            return Response.ok(Map.of(
                "message", "Historical data re-indexing started",
                "indexed", historicalIndexer.isIndexed()
            )).build();
            
        } catch (Exception e) {
            logger.severe("Error during reindex: " + e.getMessage());
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Reindex failed: " + e.getMessage()))
                    .build();
        }
    }
}
