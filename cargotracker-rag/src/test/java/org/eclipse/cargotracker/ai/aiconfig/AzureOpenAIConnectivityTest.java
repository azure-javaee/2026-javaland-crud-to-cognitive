package org.eclipse.cargotracker.ai.aiconfig;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Azure OpenAI connectivity.
 * 
 * This test requires live Azure OpenAI credentials and is disabled by default.
 * Enable by setting AZURE_OPENAI_KEY environment variable.
 * 
 * @author Brian Benz & Ed Burns
 */
public class AzureOpenAIConnectivityTest {
    
    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
    public void testChatModelCreation() {
        AiAzureOpenAIConfig config = new AiAzureOpenAIConfig();
        ChatLanguageModel chatModel = config.createChatModel();
        
        assertNotNull(chatModel, "ChatLanguageModel should be created successfully");
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
    public void testEmbeddingModelCreation() {
        AiAzureOpenAIConfig config = new AiAzureOpenAIConfig();
        EmbeddingModel embeddingModel = config.createEmbeddingModel();
        
        assertNotNull(embeddingModel, "EmbeddingModel should be created successfully");
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
    public void testSimpleChatCompletion() {
        AiAzureOpenAIConfig config = new AiAzureOpenAIConfig();
        ChatLanguageModel chatModel = config.createChatModel();
        
        assertNotNull(chatModel);
        
        String response = chatModel.generate("Say 'Hello from Cargo Tracker AI!' and nothing else.");
        
        assertNotNull(response, "Response should not be null");
        assertTrue(response.length() > 0, "Response should contain text");
        System.out.println("Azure OpenAI Response: " + response);
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
    public void testEmbeddingGeneration() {
        AiAzureOpenAIConfig config = new AiAzureOpenAIConfig();
        EmbeddingModel embeddingModel = config.createEmbeddingModel();
        
        assertNotNull(embeddingModel);
        
        var embedding = embeddingModel.embed("Cargo shipment from Hong Kong to Rotterdam").content();
        
        assertNotNull(embedding, "Embedding should not be null");
        assertTrue(embedding.dimension() > 0, "Embedding should have dimensions");
        System.out.println("Embedding dimension: " + embedding.dimension());
    }
}
