# AI Features Architecture

## Overview

The AI enhancements to Eclipse Cargo Tracker demonstrate how to modernize legacy Jakarta EE applications by embedding GenAI capabilities without "ripping apart" existing systems. All AI features are implemented as an additive layer using the `ai-` prefix for easy identification and potential removal.

## Design Principles

1. **Non-Invasive Integration**: No modifications to core domain entities (Cargo, Voyage, HandlingEvent, etc.)
2. **Additive Architecture**: All AI code is isolated in `org.eclipse.cargotracker.ai` package
3. **Graceful Degradation**: Application functions normally if AI services are unavailable
4. **Demo-Ready**: Prioritize reliability and visual impact for live demonstrations
5. **Clean Separation**: Use `ai-` prefix consistently for clear architectural boundaries

## Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| AI Framework | LangChain4j 0.35.0 | Java framework for LLM integration |
| LLM Backend | Azure OpenAI Service | Chat completions, embeddings |
| Vector Database | Qdrant | Semantic search for RAG |
| Application Server | OpenLiberty | Jakarta EE 10 runtime (Java 21 compatible) |
| Database | H2 (in-memory) | Existing application data |
| UI Framework | JSF/Facelets | Existing UI technology |
| REST | JAX-RS | AI endpoint exposure |

## Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Web Browser                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ AI Chatbot   │  │ Auto-Fill UI │  │ AI Dashboard │      │
│  │ Widget       │  │ Components   │  │ Panels       │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└───────────────────┬─────────────────────────────────────────┘
                    │ AJAX/REST
┌───────────────────┴─────────────────────────────────────────┐
│              OpenLiberty (Jakarta EE 10)                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         AI REST Endpoints (ai-rest)                  │   │
│  │  AiChatbotResource │ AiRAGResource │ AiAutoFill...  │   │
│  └────────────┬─────────────────────────────────────────┘   │
│               │                                              │
│  ┌────────────┴─────────────────────────────────────────┐   │
│  │         AI Services (ai-services)                    │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌──────────┐   │   │
│  │  │ AiChatbot   │  │ AiRAG        │  │ AiAuto   │   │   │
│  │  │ Service     │  │ QueryService │  │ Fill...  │   │   │
│  │  └─────────────┘  └──────────────┘  └──────────┘   │   │
│  └────────────┬─────────────────────────────────────────┘   │
│               │                                              │
│  ┌────────────┴─────────────────────────────────────────┐   │
│  │         AI Configuration (ai-config)                 │   │
│  │  AiAzureOpenAIConfig │ AiQdrantConfig               │   │
│  └────────────┬─────────────────────┬───────────────────┘   │
│               │                     │                        │
│  ┌────────────┴─────────┐  ┌───────┴──────────┐            │
│  │  Existing Domain     │  │  Existing        │            │
│  │  Entities (Cargo,    │  │  Repositories    │            │
│  │  HandlingEvent)      │  │  (JPA)           │            │
│  └──────────────────────┘  └──────────────────┘            │
└───────────────┬──────────────────────┬──────────────────────┘
                │                      │
     ┌──────────┴─────────┐  ┌────────┴────────┐
     │ Azure OpenAI       │  │ Qdrant Vector   │
     │ Service            │  │ Database        │
     │ (gpt-4, embeddings)│  │ (Docker)        │
     └────────────────────┘  └─────────────────┘
```

## Data Flow Diagrams

### 1. RAG Query Flow

```
User Question → AiRAGResource → AiRAGQueryService
                                      ↓
                                Embed Question (Azure OpenAI)
                                      ↓
                                Similarity Search (Qdrant)
                                      ↓
                                Retrieve Top K Documents
                                      ↓
                                Augment Prompt with Context
                                      ↓
                                Generate Answer (Azure OpenAI)
                                      ↓
                                Return Answer + Citations
```

### 2. RAG Indexing Flow (Startup)

```
Application Startup → AiHistoricalIndexer.@PostConstruct
                            ↓
                      Query All Cargo (JPA)
                            ↓
                      For Each Cargo:
                      - Build text document
                      - Add metadata (trackingId, origin, destination)
                            ↓
                      Generate Embeddings (Azure OpenAI)
                      text-embedding-ada-002
                            ↓
                      Store in Qdrant Collection
                      "cargo_tracker_history"
```

### 3. Chatbot Conversation Flow

```
User Message → AiChatbotResource → AiChatbotService
                                         ↓
                                   Parse Intent
                                   Extract Tracking ID
                                         ↓
                                   Query CargoRepository (JPA)
                                         ↓
                                   Build Context from DB
                                         ↓
                                   Add to Conversation Memory
                                         ↓
                                   Generate Response (Azure OpenAI)
                                         ↓
                                   Return Conversational Answer
```

## Feature Deep Dive: RAG (Retrieval-Augmented Generation)

### Purpose
Enable natural language queries over historical cargo data without manually writing complex SQL queries or reports.

### Components

#### AiHistoricalIndexer
- **Lifecycle**: ApplicationScoped CDI bean with @PostConstruct
- **Trigger**: Runs automatically at application startup
- **Process**:
  1. Queries all Cargo entities via JPA
  2. For each cargo, builds text document with:
     - Tracking ID
     - Origin/Destination locations
     - Route specification and itinerary
     - Current delivery status
     - Handling events summary
  3. Creates metadata map for source attribution
  4. Generates embedding using Azure OpenAI text-embedding-ada-002
  5. Stores TextSegment + Embedding + Metadata in Qdrant
- **Collection**: `cargo_tracker_history`
- **Reindex**: Manual trigger via `/api/ai/rag/reindex`

#### AiRAGQueryService
- **Lifecycle**: ApplicationScoped CDI bean
- **Query Process**:
  1. Convert user question to embedding
  2. Perform similarity search in Qdrant (top 5 results, min score 0.7)
  3. Extract relevant TextSegments
  4. Build augmented prompt with context:
     ```
     Context from historical cargo data:
     Document 1: [cargo details]
     Document 2: [cargo details]
     ...
     
     User Question: [query]
     ```
  5. Send to Azure OpenAI gpt-4
  6. Return answer with source citations (tracking IDs)
- **Features**:
  - Relevance scoring
  - Source attribution
  - Graceful handling of no results

#### AiRAGResource (REST API)
- **Base Path**: `/api/ai/rag`
- **Endpoints**:
  - `POST /query` - Submit natural language question
  - `GET /status` - Check service availability and index status
  - `POST /reindex` - Trigger manual re-indexing
- **Request Format**:
  ```json
  {
    "query": "What cargo shipments went from Hong Kong to Stockholm?"
  }
  ```
- **Response Format**:
  ```json
  {
    "answer": "Based on historical data, cargo ABC123...",
    "sources": ["Cargo ABC123 (relevance: 0.89)", "Cargo XYZ789 (relevance: 0.82)"],
    "query": "What cargo shipments went from Hong Kong to Stockholm?",
    "documentsRetrieved": 2
  }
  ```

#### AiRAGBean (JSF Backing Bean)
- **Scope**: RequestScoped
- **Properties**:
  - `query`: User's natural language question
  - `response`: AiRAGResponse object
  - `loading`: Boolean for UI state
- **Actions**:
  - `submitQuery()`: Process RAG query via AiRAGQueryService
  - `clear()`: Reset form
  - `isServiceAvailable()`: Check if AI is configured

#### ai-rag.xhtml (UI Component)
- **Location**: `/ai-rag.xhtml`
- **Features**:
  - Text area for natural language questions
  - Submit button with AJAX
  - Loading indicator during processing
  - Answer display with formatted text
  - Source citations with relevance scores
  - Service unavailability warnings
- **Styling**: Custom CSS for modern, clean interface

### Example Queries
- "What cargo went from Hong Kong to Stockholm?"
- "Show me all shipments to Rotterdam last month"
- "Which cargo had delivery issues?"
- "Find shipments on voyage V100"
- "What's the typical route from Tokyo to Hamburg?"

### Integration Points
- **Domain Model**: Read-only access via JPA EntityManager
- **Qdrant**: Docker container on localhost:6333
- **Azure OpenAI**: Embedding model + Chat model
- **UI**: Standalone page accessible from admin dashboard

### Performance Characteristics
- **Indexing**: ~100ms per cargo record
- **Query**: 2-5 seconds end-to-end
  - 200ms embedding generation
  - 100ms Qdrant search
  - 2-4s GPT-4 response
- **Scalability**: Handles 10,000+ cargo records efficiently

### Error Handling
- **No AI configured**: Returns user-friendly message
- **No relevant results**: Suggests query rephrasing
- **Azure API timeout**: Returns timeout message
- **Qdrant unavailable**: Graceful degradation message

### 3. Auto-Fill Flow

```
User Action → AiAutoFillResource → AiAutoFillService
  (Request)                              ↓
                                   AiPatternAnalyzer
                                   (Historical Data)
                                         ↓
                                   RAG Similar Shipments
                                         ↓
                                   Generate Suggestions (Azure OpenAI)
                                         ↓
                                   Return Field Suggestions

Document Upload → AiDocumentExtractor
                         ↓
                   Parse PDF/Text
                         ↓
                   Extract Structure (Azure OpenAI)
                         ↓
                   Map to Form Fields
                         ↓
                   Return Auto-Filled Data
```

### 4. Workflow Optimization Flow

```
Route Request → AiWorkflowResource → AiRouteOptimizer
                                           ↓
                                     Get Cargo Details
                                           ↓
                                     Query Historical Routes (RAG)
                                           ↓
                                     Analyze Constraints
                                     - Destination
                                     - Cargo Type
                                     - Urgency
                                     - Cost vs Speed
                                           ↓
                                     Score Available Routes (Azure OpenAI)
                                           ↓
                                     Return Top 3 Recommendations
```

## Package Structure

### `org.eclipse.cargotracker.ai.aiconfig`
Configuration beans for AI infrastructure:
- `AiAzureOpenAIConfig`: CDI producer for ChatLanguageModel and EmbeddingModel
- `AiQdrantConfig`: CDI producer for EmbeddingStore

### `org.eclipse.cargotracker.ai.aiservices`
Business logic for AI features:
- **airag**: Historical shipment intelligence
  - `AiHistoricalIndexer`: Indexes cargo data to Qdrant
  - `AiRAGQueryService`: Semantic search and Q&A
- **aichatbot**: Natural language cargo queries
  - `AiChatbotService`: Conversation management
  - `AiIntentParser`: Extract tracking IDs and intents
  - `AiCargoQueryHandler`: Query cargo status
- **aiautofill**: Smart form suggestions
  - `AiAutoFillService`: Orchestrates auto-fill features
  - `AiPatternAnalyzer`: Historical pattern analysis
  - `AiDocumentExtractor`: PDF/text extraction
  - `AiContextService`: Session-based context
- **aiworkflow**: Route and consolidation optimization
  - `AiWorkflowOptimizationService`: Main service
  - `AiRouteOptimizer`: Route suggestions
  - `AiConsolidationService`: Cargo grouping

### `org.eclipse.cargotracker.ai.airest`
JAX-RS resources exposing AI features:
- `AiChatbotResource`: `/api/ai/chatbot`
- `AiRAGResource`: `/api/ai/rag`
- `AiAutoFillResource`: `/api/ai/autofill`
- `AiWorkflowResource`: `/api/ai/workflow`

### `org.eclipse.cargotracker.ai.aiui`
JSF backing beans for UI components:
- `AiChatbotBean`: Chat widget state management
- `AiRAGBean`: RAG panel backing bean
- `AiAutoFillBean`: Auto-fill UI state
- `AiWorkflowBean`: Workflow optimization UI

## Configuration Management

### Environment Variables
```bash
AZURE_OPENAI_ENDPOINT          # Azure OpenAI resource endpoint
AZURE_OPENAI_KEY              # API key
AZURE_OPENAI_DEPLOYMENT_NAME  # Chat model deployment (gpt-4)
AZURE_OPENAI_EMBEDDING_DEPLOYMENT  # Embedding model
QDRANT_HOST                   # Qdrant host (default: localhost)
QDRANT_PORT                   # Qdrant port (default: 6333)
```

### Liberty server.xml Variables
Configuration is exposed through Liberty variables, allowing flexible override:
```xml
<variable name="azure.openai.endpoint" defaultValue="${env.AZURE_OPENAI_ENDPOINT}"/>
<variable name="qdrant.host" defaultValue="${env.QDRANT_HOST:localhost}"/>
```

### CDI Configuration Beans
AI configuration is injected via CDI throughout the application:
```java
@Inject
private ChatLanguageModel aiChatModel;

@Inject
private EmbeddingStore<TextSegment> aiEmbeddingStore;
```

## Integration with Existing Code

### Read-Only Access
AI services access existing entities through repositories:
```java
@Inject
private CargoRepository cargoRepository;

@Inject
private HandlingEventRepository handlingEventRepository;
```

### No Core Modifications
- No changes to `Cargo`, `Voyage`, `HandlingEvent` entities
- No modifications to existing service interfaces
- No changes to core business logic

### UI Integration
AI components are added to existing pages:
- Chatbot widget injected into master template
- Auto-fill buttons added to booking forms
- AI panels added to admin dashboard

## Security Considerations

1. **API Key Protection**: Never hardcode keys, use environment variables
2. **Input Validation**: Sanitize all user inputs before sending to LLM
3. **Rate Limiting**: Implement request throttling for AI endpoints
4. **Error Handling**: Don't expose internal errors to users
5. **Demo Mode**: Feature flags to disable AI for troubleshooting

## Performance Considerations

1. **Caching**: Cache embedding results and frequent queries
2. **Async Processing**: Use async operations for non-blocking AI calls
3. **Connection Pooling**: Reuse HTTP connections to Azure OpenAI
4. **Timeout Handling**: Set reasonable timeouts (30s default)
5. **Graceful Degradation**: Fall back to non-AI functionality

## Testing Strategy

### Unit Tests
- Mock LangChain4j components
- Test business logic in isolation
- 80%+ code coverage target

### Integration Tests
- Test with real Azure OpenAI (optional, requires credentials)
- Test Qdrant integration with Docker container
- End-to-end workflow tests

### Demo Reliability Tests
- Application start/stop cycles
- Azure API unavailability scenarios
- Network timeout handling
- Empty database scenarios

## Deployment

### Local Development
```bash
./mvnw liberty:dev -Popenliberty
```

### Production Considerations
- Use managed Qdrant cluster (not Docker)
- Implement proper secret management
- Add monitoring and logging
- Configure retry policies
- Set up backup AI endpoints

---

**Version**: 3.1-SNAPSHOT  
**Last Updated**: October 2025  
**Authors**: Brian Benz, Ed Burns
