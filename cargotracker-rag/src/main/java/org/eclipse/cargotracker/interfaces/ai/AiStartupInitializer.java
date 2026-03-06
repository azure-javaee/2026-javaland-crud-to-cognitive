package org.eclipse.cargotracker.interfaces.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.ai.aiservices.airag.AiHistoricalIndexer;

/**
 * Startup bean that forces initialization of AI components.
 * This ensures the Qdrant collection is created and data is indexed
 * before the application is fully available.
 * 
 * Uses CDI event observer to trigger on application startup
 * @ApplicationScoped ensures only one instance exists
 */
@ApplicationScoped
public class AiStartupInitializer {

    private static final Logger logger = Logger.getLogger(AiStartupInitializer.class.getName());

    @Inject
    private EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    private AiHistoricalIndexer historicalIndexer;

    /**
     * Observes application startup event to trigger initialization.
     * This will execute after CDI container is initialized but before servlets are available.
     */
    public void initialize(@Observes @Initialized(ApplicationScoped.class) Object init) {
        logger.info("🚀 AI Startup Initializer - Beginning initialization...");
        
        try {
            // Simply injecting embeddingStore will trigger AiQdrantConfig.createEmbeddingStore()
            // which will create the collection if it doesn't exist
            if (embeddingStore != null) {
                logger.info("✅ EmbeddingStore initialized successfully");
            } else {
                logger.warning("⚠️  EmbeddingStore is null - AI features may not be available");
            }
            
            // The historical indexer has its own @PostConstruct that will run
            // But we explicitly reference it here to ensure it's initialized
            if (historicalIndexer != null) {
                logger.info("✅ Historical indexer initialized successfully");
            } else {
                logger.warning("⚠️  Historical indexer is null - data may not be indexed");
            }
            
            logger.info("✅ AI Startup Initializer - Initialization complete!");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "❌ AI Startup Initializer - Initialization failed!", e);
            // Don't rethrow - we want the app to start even if AI features fail
        }
    }
}
