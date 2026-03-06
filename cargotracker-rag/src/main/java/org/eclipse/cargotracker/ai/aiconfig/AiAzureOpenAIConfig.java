package org.eclipse.cargotracker.ai.aiconfig;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * AI Configuration for Azure OpenAI Service integration.
 * 
 * This class provides CDI producers for LangChain4j models configured
 * to use Azure OpenAI Service. Configuration is read from environment
 * variables or Liberty server.xml variables.
 * 
 * @author Brian Benz & Ed Burns
 * @since 3.1
 */
@ApplicationScoped
public class AiAzureOpenAIConfig {
    
    private static final Logger logger = Logger.getLogger(AiAzureOpenAIConfig.class.getName());
    
    private static final String DEFAULT_ENDPOINT = "https://your-resource.openai.azure.com/";
    private static final String DEFAULT_DEPLOYMENT = "gpt-4";
    private static final String DEFAULT_EMBEDDING_DEPLOYMENT = "text-embedding-ada-002";
    
    /**
     * Produces a ChatLanguageModel configured for Azure OpenAI.
     * 
     * @return ChatLanguageModel instance
     */
    @Produces
    @ApplicationScoped
    public ChatLanguageModel createChatModel() {
        String endpoint = getConfigValue("AZURE_OPENAI_ENDPOINT", "azure.openai.endpoint", DEFAULT_ENDPOINT);
        String apiKey = getConfigValue("AZURE_OPENAI_KEY", "azure.openai.key", null);
        String deployment = getConfigValue("AZURE_OPENAI_DEPLOYMENT_NAME", "azure.openai.deployment", DEFAULT_DEPLOYMENT);
        
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warning("Azure OpenAI API key not configured. AI features will not work.");
            return null;
        }
        
        logger.info("Initializing Azure OpenAI Chat Model - Endpoint: " + endpoint + ", Deployment: " + deployment);
        
        return AzureOpenAiChatModel.builder()
                .endpoint(endpoint)
                .apiKey(apiKey)
                .deploymentName(deployment)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .logRequestsAndResponses(false)
                .build();
    }
    
    /**
     * Produces an EmbeddingModel configured for Azure OpenAI.
     * 
     * @return EmbeddingModel instance
     */
    @Produces
    @ApplicationScoped
    public EmbeddingModel createEmbeddingModel() {
        String endpoint = getConfigValue("AZURE_OPENAI_ENDPOINT", "azure.openai.endpoint", DEFAULT_ENDPOINT);
        String apiKey = getConfigValue("AZURE_OPENAI_KEY", "azure.openai.key", null);
        String deployment = getConfigValue("AZURE_OPENAI_EMBEDDING_DEPLOYMENT", "azure.openai.embedding.deployment", DEFAULT_EMBEDDING_DEPLOYMENT);
        
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warning("Azure OpenAI API key not configured. Embedding features will not work.");
            return null;
        }
        
        logger.info("Initializing Azure OpenAI Embedding Model - Endpoint: " + endpoint + ", Deployment: " + deployment);
        
        return AzureOpenAiEmbeddingModel.builder()
                .endpoint(endpoint)
                .apiKey(apiKey)
                .deploymentName(deployment)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .logRequestsAndResponses(false)
                .build();
    }
    
    /**
     * Gets configuration value from environment variable or system property.
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
}
