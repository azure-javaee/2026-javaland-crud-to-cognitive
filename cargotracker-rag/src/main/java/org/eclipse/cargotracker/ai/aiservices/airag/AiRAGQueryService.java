package org.eclipse.cargotracker.ai.aiservices.airag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) Query Service.
 * 
 * Implements semantic search over historical cargo data:
 * 1. Convert user query to embedding
 * 2. Find similar documents in Qdrant
 * 3. Augment prompt with retrieved context
 * 4. Generate AI answer with source citations
 * 
 * @author Brian Benz & Ed Burns
 * @since 3.1
 */
@ApplicationScoped
public class AiRAGQueryService {
    
    private static final Logger logger = Logger.getLogger(AiRAGQueryService.class.getName());
    
    private static final int MAX_RESULTS = 5; // Top K similar documents
    private static final double MIN_RELEVANCE_SCORE = 0.7; // Minimum similarity threshold
    
    @Inject
    private ChatLanguageModel chatModel;
    
    @Inject
    private EmbeddingModel embeddingModel;
    
    @Inject
    private EmbeddingStore<TextSegment> embeddingStore;
    
    @Inject
    private AiHistoricalIndexer historicalIndexer;
    
    private boolean indexAttempted = false;
    
    /**
     * Query historical cargo data using RAG.
     * Automatically indexes data on first query if not already indexed.
     * 
     * @param query The user's natural language query
     * @return RAG response with answer and sources
     */
    public AiRAGResponse query(String query) {
        if (chatModel == null || embeddingModel == null || embeddingStore == null) {
            logger.warning("AI models not configured. RAG query cannot be processed.");
            return new AiRAGResponse(
                "AI services are not currently available. Please configure Azure OpenAI and Qdrant.",
                List.of(),
                query,
                0
            );
        }
        
        // Auto-index on first query if not already done
        if (!indexAttempted && historicalIndexer != null && !historicalIndexer.isIndexed()) {
            logger.info("Data not indexed yet. Indexing now...");
            indexAttempted = true;
            historicalIndexer.indexHistoricalData();
        }
        
        if (query == null || query.trim().isEmpty()) {
            return new AiRAGResponse(
                "Please provide a query.",
                List.of(),
                query,
                0
            );
        }
        
        try {
            logger.info("Processing RAG query: " + query);
            
            // Step 1: Convert query to embedding
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            
            // Step 2: Find similar documents in Qdrant
            List<EmbeddingMatch<TextSegment>> relevantMatches = embeddingStore.findRelevant(
                queryEmbedding,
                MAX_RESULTS,
                MIN_RELEVANCE_SCORE
            );
            
            if (relevantMatches.isEmpty()) {
                logger.info("No relevant documents found for query: " + query);
                return new AiRAGResponse(
                    "I couldn't find any relevant cargo information for your query. Try rephrasing or asking about specific tracking IDs, locations, or routes.",
                    List.of(),
                    query,
                    0
                );
            }
            
            logger.info("Found " + relevantMatches.size() + " relevant documents");
            
            // Step 3: Extract context and sources
            StringBuilder contextBuilder = new StringBuilder();
            List<String> sources = new ArrayList<>();
            
            for (int i = 0; i < relevantMatches.size(); i++) {
                EmbeddingMatch<TextSegment> match = relevantMatches.get(i);
                TextSegment segment = match.embedded();
                
                contextBuilder.append("Document ").append(i + 1).append(":\n");
                contextBuilder.append(segment.text()).append("\n\n");
                
                // Extract tracking ID from metadata
                if (segment.metadata() != null && segment.metadata().containsKey("trackingId")) {
                    sources.add("Cargo " + segment.metadata().get("trackingId") + 
                               " (relevance: " + String.format("%.2f", match.score()) + ")");
                }
            }
            
            // Step 4: Build augmented prompt
            String augmentedPrompt = buildAugmentedPrompt(query, contextBuilder.toString());
            
            // Step 5: Generate answer using chat model
            List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful cargo tracking assistant. Use the provided context to answer questions about cargo shipments accurately. If the context doesn't contain enough information, say so."),
                UserMessage.from(augmentedPrompt)
            );
            
            Response<AiMessage> response = chatModel.generate(messages);
            String answer = response.content().text();
            
            logger.info("Generated RAG answer successfully");
            
            return new AiRAGResponse(
                answer,
                sources,
                query,
                relevantMatches.size()
            );
            
        } catch (Exception e) {
            logger.severe("Error processing RAG query: " + e.getMessage());
            e.printStackTrace();
            
            return new AiRAGResponse(
                "An error occurred while processing your query: " + e.getMessage(),
                List.of(),
                query,
                0
            );
        }
    }
    
    /**
     * Build augmented prompt with retrieved context.
     * 
     * @param query User query
     * @param context Retrieved documents
     * @return Augmented prompt
     */
    private String buildAugmentedPrompt(String query, String context) {
        return String.format(
            "Context from historical cargo data:\n%s\n\n" +
            "User Question: %s\n\n" +
            "Please answer the question based on the context provided above. " +
            "If the context contains relevant information, use it to provide a detailed answer. " +
            "If the context doesn't fully answer the question, mention what information is available. " +
            "Be specific and cite tracking IDs when relevant.",
            context,
            query
        );
    }
    
    /**
     * Check if RAG service is available.
     * 
     * @return true if all required components are configured
     */
    public boolean isAvailable() {
        return chatModel != null && embeddingModel != null && embeddingStore != null;
    }
}
