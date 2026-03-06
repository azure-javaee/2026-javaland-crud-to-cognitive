# AI Features Developer Guide

## Setup Instructions

### Architecture Overview

The AI features are implemented as a separate layer that integrates with the existing Cargo Tracker application without modifying core domain logic.

**Key Components:**
- **ai-config**: Configuration beans for Azure OpenAI and Qdrant
- **ai-services**: Business logic for AI features (RAG, chatbot, auto-fill, workflow)
- **ai-rest**: REST endpoints for AI operations
- **ai-ui**: JSF backing beans for AI UI components
- **ai-utils**: Utility classes for error handling, logging

### AI Features

1. **RAG (Retrieval-Augmented Generation)**
   - Semantic search over historical shipment data
   - Question answering with context from vector database
   - UI Page: `/ai-rag.xhtml`
   - REST API: `/api/ai/rag/query`

2. **AI Chatbot**
   - Natural language cargo status queries
   - Floating widget on all pages
   - Session-based conversation memory

3. **Smart Auto-Fill**
   - Historical pattern analysis
   - Document extraction (PDF, text)
   - Context-aware suggestions

4. **Workflow Optimization**
   - AI-powered route suggestions
   - Cargo consolidation recommendations

## Using RAG (Retrieval-Augmented Generation)

### What is RAG?
RAG combines semantic search with AI text generation to answer questions about historical cargo data. Instead of writing complex SQL queries or reports, users can ask questions in natural language.

### How It Works
1. **Indexing** (happens at startup):
   - All cargo records are converted to text documents
   - Documents are embedded using Azure OpenAI
   - Embeddings are stored in Qdrant vector database
   - Metadata (tracking IDs, locations) preserved for citations

2. **Querying** (user-initiated):
   - User asks a natural language question
   - Question is converted to an embedding
   - Qdrant finds the most similar documents
   - Retrieved documents augment the AI prompt
   - GPT-4 generates an answer with source citations

### Web UI Usage

**Access the RAG interface:**
```
http://localhost:8080/cargo-tracker/ai-rag.xhtml
```

**Example Questions:**
- "What cargo shipments went from Hong Kong to Stockholm?"
- "Show me shipments on voyage V100"
- "Which cargo had routing problems?"
- "Find all deliveries to Rotterdam"
- "What's the typical route from Tokyo to Hamburg?"

**UI Features:**
- Natural language input text area
- Real-time loading indicator
- Answer display with formatted text
- Source citations with relevance scores
- Service availability warnings

### REST API Usage

**Check RAG service status:**
```bash
curl http://localhost:8080/cargo-tracker/api/ai/rag/status
```

Response:
```json
{
  "available": true,
  "indexed": true,
  "message": "RAG service is ready"
}
```

**Submit a query:**
```bash
curl -X POST http://localhost:8080/cargo-tracker/api/ai/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What cargo went from Hong Kong to Stockholm?"
  }'
```

Response:
```json
{
  "answer": "Based on historical data, cargo ABC123 was shipped from Hong Kong to Stockholm...",
  "sources": [
    "Cargo ABC123 (relevance: 0.89)",
    "Cargo XYZ789 (relevance: 0.82)"
  ],
  "query": "What cargo went from Hong Kong to Stockholm?",
  "documentsRetrieved": 2
}
```

**Trigger re-indexing:**
```bash
curl -X POST http://localhost:8080/cargo-tracker/api/ai/rag/reindex
```

### Programmatic Usage

**Inject the service:**
```java
@Inject
private AiRAGQueryService ragQueryService;

public void searchCargo() {
    AiRAGResponse response = ragQueryService.query(
        "Find shipments to Rotterdam"
    );
    
    System.out.println("Answer: " + response.getAnswer());
    System.out.println("Sources: " + response.getSources());
    System.out.println("Documents: " + response.getDocumentsRetrieved());
}
```

**Check service availability:**
```java
if (ragQueryService.isAvailable()) {
    // RAG is configured and ready
} else {
    // Fall back to traditional search
}
```

**Trigger manual indexing:**
```java
@Inject
private AiHistoricalIndexer indexer;

public void refreshIndex() {
    indexer.reindex();
}
```

### Configuration

**Qdrant Collection:**
- Collection name: `cargo_tracker_history`
- Vector size: 1536 (Ada-002 dimensions)
- Distance metric: Cosine similarity

**Search Parameters:**
- Max results: 5 documents
- Min relevance score: 0.7
- Timeout: 30 seconds

### Performance Tips

1. **Initial Indexing**: Takes ~100ms per cargo record at startup
2. **Query Time**: Typically 2-5 seconds:
   - 200ms embedding generation
   - 100ms vector search
   - 2-4s GPT-4 response
3. **Caching**: Consider caching frequent queries
4. **Re-indexing**: Only needed when data changes significantly

### Troubleshooting RAG

**No results found:**
- Check if historical indexer ran successfully
- Verify Qdrant container is running: `docker ps | grep qdrant`
- Try rephrasing the question
- Lower relevance threshold if needed

**Slow query performance:**
- Check network latency to Azure OpenAI
- Verify Qdrant is running locally (not remote)
- Monitor Azure OpenAI quota usage

**Indexing errors:**
- Check application logs at startup
- Verify EntityManager is injecting properly
- Ensure Qdrant collection was created
- Check embeddings model deployment name

### Development Guidelines

#### Naming Conventions
All AI code uses the `ai-` prefix:
- Packages: `org.eclipse.cargotracker.ai.aiconfig`
- Classes: `AiChatbotService`, `AiRAGQueryService`
- Files: `ai-chatbot.js`, `ai-styles.css`
- UI Components: `ai-chatbot-widget.xhtml`

#### Testing
Run tests with:
```bash
./mvnw test -Popenliberty
```

#### Adding New AI Features
1. Create service class in appropriate `ai-services` subpackage
2. Add REST endpoint in `ai-rest` package
3. Create UI component in `webapp/ai-components`
4. Write unit tests in `src/test/java/.../ai/`
5. Update documentation

### Troubleshooting

**Qdrant not starting**
- Ensure Docker is running
- Check port 6333 is not in use: `netstat -an | grep 6333`
- View logs: `docker logs cargotracker-qdrant`

**Azure OpenAI connection errors**
- Verify environment variables are set correctly
- Check API key is valid
- Ensure deployment names match your Azure OpenAI resource

**Dependencies not resolving**
- Clean and rebuild: `./mvnw clean install -Popenliberty`
- Check Maven is using Java 21: `./mvnw -version`

For more issues, see [ai-troubleshooting.md](ai-troubleshooting.md)

### Project Structure

```
src/main/java/org/eclipse/cargotracker/ai/
├── aiconfig/           # Configuration
├── aiservices/         # Business logic
│   ├── airag/
│   ├── aichatbot/
│   ├── aiautofill/
│   └── aiworkflow/
├── airest/            # REST endpoints
├── aiui/              # JSF backing beans
└── aiutils/           # Utilities

src/main/webapp/
├── ai-components/     # AI UI components
└── resources/
    ├── css/ai-styles.css
    └── js/
        ├── ai-chatbot.js
        └── ai-autofill.js

ai-docs/              # Documentation
├── diagrams/
└── screenshots/
```

### Next Steps

- See [ai-architecture.md](ai-architecture.md) for detailed architecture

---

**Version**: 3.1-SNAPSHOT
**Updated**: 2026-03-06
**Authors**: Brian Benz, Ed Burns
