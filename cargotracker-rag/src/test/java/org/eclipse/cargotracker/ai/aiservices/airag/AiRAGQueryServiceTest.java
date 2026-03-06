package org.eclipse.cargotracker.ai.aiservices.airag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AiRAGQueryService.
 * 
 * Tests RAG query flow with mocked LangChain4j components.
 * 
 * @author Brian Benz & Ed Burns
 * @since 3.1
 */
@ExtendWith(MockitoExtension.class)
class AiRAGQueryServiceTest {
    
    @Mock
    private ChatLanguageModel chatModel;
    
    @Mock
    private EmbeddingModel embeddingModel;
    
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;
    
    @InjectMocks
    private AiRAGQueryService ragQueryService;
    
    @BeforeEach
    void setUp() {
        // Service will be injected with mocks
    }
    
    @Test
    void testQueryWithNoModelsConfigured() {
        // Given: Service with null models
        ragQueryService = new AiRAGQueryService();
        
        // When: Query is submitted
        AiRAGResponse response = ragQueryService.query("Test query");
        
        // Then: Should return unavailable message
        assertNotNull(response);
        assertTrue(response.getAnswer().contains("not currently available"));
        assertEquals(0, response.getDocumentsRetrieved());
    }
    
    @Test
    void testQueryWithEmptyString() {
        // When: Empty query is submitted
        AiRAGResponse response = ragQueryService.query("");
        
        // Then: Should return error message
        assertNotNull(response);
        assertTrue(response.getAnswer().contains("provide a query"));
    }
    
    @Test
    void testQueryWithNullString() {
        // When: Null query is submitted
        AiRAGResponse response = ragQueryService.query(null);
        
        // Then: Should return error message
        assertNotNull(response);
        assertTrue(response.getAnswer().contains("provide a query"));
    }
    
    @Test
    void testQueryWithNoRelevantDocuments() {
        // Given: Embedding model returns embedding
        dev.langchain4j.model.output.Response<Embedding> embeddingResponse = 
            mock(dev.langchain4j.model.output.Response.class);
        Embedding embedding = mock(Embedding.class);
        when(embeddingResponse.content()).thenReturn(embedding);
        when(embeddingModel.embed(anyString())).thenReturn(embeddingResponse);
        
        // And: Embedding store returns no matches
        when(embeddingStore.findRelevant(any(Embedding.class), anyInt(), anyDouble()))
            .thenReturn(Collections.emptyList());
        
        // When: Query is submitted
        AiRAGResponse response = ragQueryService.query("Find shipments from Tokyo");
        
        // Then: Should return no results message
        assertNotNull(response);
        assertTrue(response.getAnswer().contains("couldn't find any relevant"));
        assertEquals(0, response.getDocumentsRetrieved());
    }
    
    @Test
    void testSuccessfulQuery() {
        // Given: Embedding model returns embedding
        dev.langchain4j.model.output.Response<Embedding> embeddingResponse = 
            mock(dev.langchain4j.model.output.Response.class);
        Embedding embedding = mock(Embedding.class);
        when(embeddingResponse.content()).thenReturn(embedding);
        when(embeddingModel.embed(anyString())).thenReturn(embeddingResponse);
        
        // And: Embedding store returns relevant match
        TextSegment segment = TextSegment.from(
            "Cargo ABC123 from Hong Kong to Stockholm",
            new dev.langchain4j.data.document.Metadata()
                .put("trackingId", "ABC123")
                .put("origin", "Hong Kong")
                .put("destination", "Stockholm")
        );
        
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
            0.85,
            "match-id",
            embedding,
            segment
        );
        
        when(embeddingStore.findRelevant(any(Embedding.class), anyInt(), anyDouble()))
            .thenReturn(List.of(match));
        
        // And: Chat model returns answer
        AiMessage aiMessage = AiMessage.from("Found cargo ABC123 shipped from Hong Kong to Stockholm");
        Response<AiMessage> chatResponse = Response.from(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(chatResponse);
        
        // When: Query is submitted
        AiRAGResponse response = ragQueryService.query("Find shipments from Hong Kong");
        
        // Then: Should return successful response
        assertNotNull(response);
        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("ABC123") || 
                   response.getAnswer().contains("Hong Kong") ||
                   response.getAnswer().contains("Stockholm"));
        assertEquals(1, response.getDocumentsRetrieved());
        assertFalse(response.getSources().isEmpty());
        assertTrue(response.getSources().get(0).contains("ABC123"));
    }
    
    @Test
    void testIsAvailableWithAllModels() {
        // When: All models are configured (mocked)
        boolean available = ragQueryService.isAvailable();
        
        // Then: Service should be available
        assertTrue(available);
    }
    
    @Test
    void testIsAvailableWithNoModels() {
        // Given: Service with no models
        ragQueryService = new AiRAGQueryService();
        
        // When: Check availability
        boolean available = ragQueryService.isAvailable();
        
        // Then: Service should not be available
        assertFalse(available);
    }
}
