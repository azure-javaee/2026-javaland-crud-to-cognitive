# From CRUD to Cognitive: What Brian Changed

## Executive Summary

Brian took the Eclipse Cargo Tracker — a standard Jakarta EE 10 CRUD application — and added a **Retrieval-Augmented Generation (RAG)** layer that lets users ask natural language questions about historical cargo shipments. The AI features were added as a **purely additive layer**: zero modifications to any existing domain entity, repository, or service class. Every AI artifact lives under an `ai-` prefixed package and can be cleanly removed.

The architecture docs and `.env.example` show feature toggles for the others, but no code exists.

---

## What Was Added (File Inventory)

### New Java Classes (8 files)

| File | Package | Role |
|------|---------|------|
| `AiAzureOpenAIConfig.java` | `ai.aiconfig` | CDI `@Produces` for `ChatLanguageModel` and `EmbeddingModel` via Azure OpenAI |
| `AiQdrantConfig.java` | `ai.aiconfig` | CDI `@Produces` for `EmbeddingStore<TextSegment>` backed by Qdrant |
| `AiHistoricalIndexer.java` | `ai.aiservices.airag` | Reads `Cargo` + `HandlingEvent` entities from JPA, builds text documents, embeds via Azure OpenAI, stores in Qdrant |
| `AiRAGQueryService.java` | `ai.aiservices.airag` | Embeds user query → semantic search in Qdrant → augments prompt with context → calls GPT-4o → returns answer with citations |
| `AiRAGResponse.java` | `ai.aiservices.airag` | Simple response DTO (answer, sources, query, documentsRetrieved) |
| `AiStartupInitializer.java` | `interfaces.ai` | CDI observer that eagerly initializes AI components on app startup |
| `AiRAGResource.java` | `interfaces.rest` | JAX-RS REST endpoint: `POST /rest/ai/rag/query`, `GET /rest/ai/rag/status`, `POST /rest/ai/rag/reindex` |
| `AiRAGBean.java` | `interfaces.backing` | JSF backing bean for the `ai-rag.xhtml` page |

### New Test Classes (2 files)

| File | What it Tests |
|------|---------------|
| `AzureOpenAIConnectivityTest.java` | Integration test (guarded by `@EnabledIfEnvironmentVariable`) — verifies chat model creation, embedding generation |
| `AiRAGQueryServiceTest.java` | Unit test with Mockito — tests null handling, empty queries, no-results scenarios |

### New Configuration / Infrastructure

| File | Purpose |
|------|---------|
| `.env.example` | Template for Azure OpenAI + Qdrant connection settings |
| `ai-setup.sh` | Bash script: validates Java 21 + Docker, starts Qdrant Docker container (v1.11.5) |
| `ai-setup.ps1` | PowerShell equivalent of `ai-setup.sh` |
| `qdrant-setup.sh` | Minimal Qdrant Docker startup script |
| `ai-rag.xhtml` | JSF page with text area for natural language queries and styled response display |

### New Documentation

| File | Content |
|------|---------|
| `AI_README.md` | Setup guide for AI features |
| `AI_QUICKSTART.md` | Quick start instructions |
| `ai-docs/ai-architecture.md` | Architecture overview with ASCII diagrams |
| `ai-docs/ai-developer-guide.md` | Developer guide for extending AI features |
| `ai-docs/phase2-rag-complete.md` through `phase6-documentation-demo.md` | Phase-by-phase implementation notes |
| `FIX_GUAVA.md`, `TROUBLESHOOTING.md`, `VERIFY_DEPENDENCIES.md` | Troubleshooting guides for common issues |

### Modified Files (3 files)

| File | What Changed |
|------|--------------|
| `pom.xml` | Added 7 new dependencies (LangChain4j suite + Guava) + `maven-dependency-plugin` to copy JARs to Liberty shared resources |
| `server.xml` | Added 6 Liberty `<variable>` elements for AI config, a `<library id="aiLib">` for LangChain4j JARs, and added `aiLib` to the app `<classloader>` |
| `.gitignore` | Added `.env.edburns` (personal secrets file) |

---

## Libraries Used

### LangChain4j 0.35.0 (5 artifacts)

| Artifact | Purpose |
|----------|---------|
| `langchain4j` | Core framework: `TextSegment`, `Embedding`, `ChatMessage`, document abstractions |
| `langchain4j-azure-open-ai` | Azure OpenAI integration: `AzureOpenAiChatModel`, `AzureOpenAiEmbeddingModel` |
| `langchain4j-qdrant` | Qdrant vector store integration: `QdrantEmbeddingStore` (bundles `io.qdrant:client:1.11.0`) |
| `langchain4j-embeddings` | Embedding model abstractions |
| `langchain4j-document-parser-apache-pdfbox` | PDF parsing (declared but not used in any code — intended for future auto-fill feature) |

### Guava 33.0.0-jre + failureaccess 1.0.2

Required transitive dependency of the `io.qdrant:client` gRPC stack. Had to be explicitly declared because Liberty's classloading model needs JARs in the shared resources directory.

### Mockito 5.14.2

Added for unit testing the RAG service with mocked LangChain4j components.

---

## Technologies Used

| Technology | Version | Role |
|------------|---------|------|
| **Azure OpenAI Service** | GPT-4o + text-embedding-3-small | LLM for chat completions and 1536-dim embeddings |
| **Qdrant** | v1.11.5 (Docker) | Vector database for semantic similarity search |
| **LangChain4j** | 0.35.0 | Java LLM orchestration framework |
| **gRPC** | (via Qdrant client) | Communication protocol between app and Qdrant |
| **CDI (Contexts and Dependency Injection)** | Jakarta CDI 4.0 | Used for `@Produces` pattern to wire AI components |
| **JPA** | Jakarta Persistence 3.1 | Reads existing domain entities for indexing |
| **JAX-RS** | Jakarta REST 3.1 | Exposes AI endpoints |
| **JSF/Facelets** | Jakarta Faces 4.0 | New `ai-rag.xhtml` UI page |

---

## Deep Dive: How Each Core Technology Is Used

### Azure OpenAI Service — What It Does in This App

Azure OpenAI Service is a cloud API hosted by Microsoft that gives you access to OpenAI's AI models (like GPT-4) through your Azure subscription. Think of it as a remote brain that your app calls over the internet. You send it text, it sends back intelligent responses. This app uses it for **two completely different tasks**, each handled by a separate "deployment" (Azure's term for a running instance of a model):

**Deployment 1: `text-embedding-3-small` (the Embedding Model)**

This model converts text into a list of 1,536 numbers (called a "vector" or "embedding"). These numbers represent the *meaning* of the text in a way that math can work with. Two pieces of text that mean similar things will produce similar lists of numbers. For example, "cargo shipped from Hong Kong" and "shipment originating in Hong Kong" would produce vectors that are close together numerically, even though the words are different.

There's nothing inherently special about 1,536 — it's just the output dimension that OpenAI chose for this particular model. Different embedding models use different dimensions: `text-embedding-3-large` produces 3,072 numbers, Cohere Embed v3 uses 1,024, and open-source Sentence-BERT models typically use 384 or 768. Higher dimensions can capture more nuance in meaning but cost more to store and search. 1,536 is a middle ground — enough fidelity for good similarity matching without excessive storage or compute costs. The only hard requirement is that **everything must agree on the same number**: the embedding model produces 1,536-dim vectors, the Qdrant collection is configured with `setSize(1536)`, and the query embedding must also be 1,536-dim. If any of these mismatch, you get a dimension error at runtime.

In this app, the embedding model is called in two places:

1. **During indexing** (`AiHistoricalIndexer`): For each cargo record and handling event in the database, the app builds a plain-English text description (e.g., "Cargo Tracking ID: ABC123, Origin: Hong Kong, Destination: Helsinki...") and sends it to Azure. Azure returns 1,536 numbers. Those numbers get stored in Qdrant (the vector database) alongside the original text.

2. **During querying** (`AiRAGQueryService`): When a user types a question like "Which cargo went through customs?", that question is also sent to Azure to get its 1,536 numbers. Those numbers are then compared against all the stored numbers to find the most similar documents.

**Deployment 2: `gpt-4o` (the Chat Model)**

This is the large language model — the "AI" that actually understands language and generates answers. It's used **only at query time**, after the relevant documents have already been found. The app sends GPT-4o a message that looks roughly like:

```
System: You are a helpful cargo tracking assistant. Use the provided context
       to answer questions about cargo shipments accurately.

User: Context from historical cargo data:
      Document 1: Cargo Tracking ID: MNO456, Origin: New York, Destination: Dallas...
      Document 2: Handling Event for Cargo: MNO456, Event Type: CUSTOMS, Location: Dallas...

      User Question: Which cargo went through customs?

      Please answer based on the context above.
```

GPT-4o reads the context documents and the question, then generates a natural-language answer: "Cargo MNO456 went through customs in Dallas on 2026-02-04." It doesn't search a database — it just reads the text you gave it and writes a response. The key insight is that **without the context documents, GPT-4o would have no idea what's in your database**. That's why the retrieval step (finding relevant documents) happens first.

**How it connects to the code**: In `AiAzureOpenAIConfig.java`, two CDI producer methods create these models by reading environment variables (`AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_KEY`, `AZURE_OPENAI_DEPLOYMENT_NAME`, `AZURE_OPENAI_EMBEDDING_DEPLOYMENT`) and calling `AzureOpenAiChatModel.builder()` and `AzureOpenAiEmbeddingModel.builder()`. Once built, these objects are injected anywhere the app needs them via `@Inject`.

### Qdrant — What It Does in This App

Qdrant (pronounced "quadrant") is a **vector database** — a specialized database designed to store and search lists of numbers (vectors) very quickly. A regular database like PostgreSQL finds rows by matching exact column values (e.g., `WHERE city = 'Dallas'`). Qdrant instead finds rows by asking "which stored vectors are most mathematically similar to this query vector?" This is called **similarity search** or **nearest-neighbor search**.

In this app, Qdrant runs as a Docker container on `localhost`. It exposes two ports:
- **6333**: An HTTP REST API (used for health checks like `curl http://localhost:6333/collections/cargo_tracker_history`)
- **6334**: A gRPC API (used by the Java app for all actual read/write operations — gRPC is faster than REST for programmatic use)

**The collection**: Qdrant organizes data into "collections" (similar to tables in SQL). This app has one collection called `cargo_tracker_history`. It's configured for:
- **1,536 dimensions**: Each stored vector has 1,536 numbers (matching the output of `text-embedding-3-small`)
- **Cosine distance**: The similarity metric. Cosine distance measures the angle between two vectors — vectors pointing in the same direction are considered similar regardless of their length.

**What's stored in each point**: A Qdrant "point" (similar to a row) contains:
- An **ID** (auto-generated UUID)
- A **vector**: The 1,536 numbers from Azure OpenAI's embedding model
- A **payload**: The original text plus metadata (tracking ID, origin, destination, event type, etc.)

After indexing, the collection has **16 points**: 4 cargo summaries + 12 handling events.

**How it's used at query time**: When a user asks "Has any cargo been through customs?", the app:
1. Converts the question to a 1,536-number vector via Azure OpenAI
2. Sends that vector to Qdrant with a request: "find me the 5 most similar points with a cosine similarity score ≥ 0.7"
3. Qdrant compares the query vector against all 16 stored vectors and returns the top matches
4. The app extracts the original text from those matches and hands it to GPT-4o

**How it connects to the code**: `AiQdrantConfig.java` creates a `QdrantClient` (gRPC connection) and a `QdrantEmbeddingStore` (LangChain4j's wrapper around that client). The `createCollectionIfNotExists` method creates the collection on first run. The `EmbeddingStore<TextSegment>` interface hides all of this — the rest of the app just calls `embeddingStore.addAll(embeddings, segments)` to write and `embeddingStore.findRelevant(queryEmbedding, 5, 0.7)` to search.

### LangChain4j — What It Does in This App

LangChain4j is a **Java framework that provides the glue between your application, AI models, and vector databases**. Without it, you'd need to:
- Manually call Azure OpenAI's REST API, parse JSON responses, handle retries and timeouts
- Manually format chat messages into the exact JSON structure OpenAI expects
- Manually call Qdrant's gRPC API, serialize/deserialize protobuf messages, manage connections
- Write your own code to split documents into chunks, track metadata, match embeddings to text

LangChain4j replaces all of that with clean Java interfaces. Here's exactly what this app uses from it:

**Core abstractions (from `langchain4j` artifact)**:
- `TextSegment`: A piece of text with attached `Metadata` (key-value pairs). Each cargo description and handling event description becomes a `TextSegment`.
- `Embedding`: A wrapper around a `float[]` array — the vector of numbers that represents a piece of text.
- `ChatMessage`, `SystemMessage`, `UserMessage`, `AiMessage`: Typed objects representing the messages in a conversation with GPT-4o. The `SystemMessage` sets the persona ("You are a helpful cargo tracking assistant..."), the `UserMessage` contains the augmented prompt, and the `AiMessage` is what comes back.
- `Document` and `Metadata`: Document abstractions for text with metadata tracking.

**Azure OpenAI integration (from `langchain4j-azure-open-ai` artifact)**:
- `AzureOpenAiChatModel`: Implements `ChatLanguageModel`. You call `.generate(messages)` and it handles the HTTP call to `https://your-resource.openai.azure.com/`, authentication, retry logic, and response parsing. Configured with a builder: `.endpoint(...)`, `.apiKey(...)`, `.deploymentName(...)`, `.temperature(0.7)`.
- `AzureOpenAiEmbeddingModel`: Implements `EmbeddingModel`. You call `.embed("some text")` and it returns an `Embedding` (the 1,536-number vector). Same builder pattern.

**Qdrant integration (from `langchain4j-qdrant` artifact)**:
- `QdrantEmbeddingStore`: Implements `EmbeddingStore<TextSegment>`. Provides `.addAll(embeddings, segments)` to store vectors + text, and `.findRelevant(queryEmbedding, maxResults, minScore)` to search. Internally talks to Qdrant over gRPC.

**The RAG pipeline in concrete terms**: Here's the exact sequence of LangChain4j calls when a user asks a question in this app:

```
// Step 1: Convert question to numbers
Embedding queryEmbedding = embeddingModel.embed("Has any cargo been through customs?").content();
// → Calls Azure OpenAI text-embedding-3-small, returns 1,536 floats

// Step 2: Find similar stored documents
List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, 5, 0.7);
// → Sends vector to Qdrant over gRPC, gets back up to 5 matches with score ≥ 0.7
// → Each match contains the original TextSegment (text + metadata) and a similarity score

// Step 3: Build prompt with retrieved context
// (This is plain Java string building — no LangChain4j magic here)

// Step 4: Send to GPT-4o
List<ChatMessage> messages = List.of(
    SystemMessage.from("You are a helpful cargo tracking assistant..."),
    UserMessage.from(augmentedPrompt)
);
Response<AiMessage> response = chatModel.generate(messages);
String answer = response.content().text();
// → Calls Azure OpenAI gpt-4o, returns natural language answer
```

That's the entire AI pipeline: **embed → search → augment → generate**. LangChain4j makes each step a single method call.

---

## Why These Technologies? (And Alternatives)

### LangChain4j — Why a Good Choice

- **Java-native**: Unlike LangChain (Python), LangChain4j is designed for Java/Jakarta EE. It integrates naturally with CDI, provides typed APIs, and runs within the same JVM as the app server.
- **Abstraction layer**: The `ChatLanguageModel`, `EmbeddingModel`, and `EmbeddingStore` interfaces mean you can swap providers (Azure → OpenAI → Ollama → Anthropic) without changing service code.
- **All-in-one**: Single framework handles embeddings, vector store integration, document chunking, and prompt construction.
- **Active community**: Fast-growing project with frequent releases.

#### Alternatives

| Alternative | Trade-off |
|-------------|-----------|
| **Spring AI** | Requires Spring Boot. Cargo Tracker is Jakarta EE on Liberty — would need a full runtime migration. |
| **Semantic Kernel (Java)** | Microsoft's SDK. Good Azure integration but less community momentum on the Java side; more C#/.NET focused. |
| **Direct Azure OpenAI SDK** | `com.azure:azure-ai-openai`. Lower-level, more boilerplate. Would need to implement RAG pipeline manually. |
| **Quarkus LangChain4j** | Same underlying library but tightly coupled to Quarkus runtime. Not viable on Liberty. |

#### Future Risks

- LangChain4j API is **not yet stable** (pre-1.0). Expect breaking changes between 0.35 → 1.0.
- Transitive dependency tree is **large** (~50+ JARs copied to Liberty shared resources). Potential classloading conflicts.
- The `@Produces` returning `null` pattern for CDI is **fragile** — CDI spec doesn't guarantee null producers work correctly across all implementations.

### Azure OpenAI — Why a Good Choice

- **Enterprise features**: Azure-managed API keys, VNet integration, content filtering, data residency.
- **Same models as OpenAI**: GPT-4o, text-embedding-3-small — full access to latest models.
- **Compliance**: SOC 2, HIPAA, ISO 27001 — matters for enterprise Java shops.

#### Alternatives

| Alternative | Trade-off |
|-------------|-----------|
| **OpenAI direct** | Simpler setup, no Azure subscription needed. Loses enterprise security/compliance features. |
| **Ollama (local)** | Free, private, no network dependency. Models are smaller/less capable. Good for dev/testing. |
| **Amazon Bedrock** | AWS-native. Would use `langchain4j-amazon-bedrock` module. Same architectural pattern. |
| **Google Vertex AI** | GCP-native. Gemini models. Same architectural pattern. |

#### Future Risks

- **Vendor lock-in**: While LangChain4j abstracts the provider, the `.env` config, deployment scripts, and CDI producers are Azure-specific.
- **Cost**: Azure OpenAI bills per token. High-volume RAG queries (embed + retrieve + generate) add up.
- **Model deprecation**: Azure retires model versions. `gpt-4o` and `text-embedding-3-small` deployments will need updates.

### Qdrant — Why a Good Choice

- **Purpose-built**: Vector similarity search with filtering, unlike bolted-on vector support in PostgreSQL (pgvector).
- **Docker-friendly**: Single `docker run` command for local dev.
- **gRPC + REST**: Both APIs available. LangChain4j uses gRPC for performance.
- **Open source**: Apache 2.0 license.

#### Alternatives

| Alternative | Trade-off |
|-------------|-----------|
| **pgvector (PostgreSQL)** | Reuse existing database. Simpler ops but slower at scale. No dedicated vector search optimizations. |
| **Weaviate** | Similar feature set. Slightly more complex setup but has built-in vectorization modules. |
| **Pinecone** | Managed service, zero ops. But proprietary and adds cloud dependency. |
| **ChromaDB** | Simpler API, Python-first. Java support is limited. |
| **In-memory (`langchain4j-embeddings`)** | LangChain4j has `InMemoryEmbeddingStore`. Zero infrastructure. Fine for demos with 16 documents, not for production. |

**Notable**: For a demo with only 16 documents, Qdrant is overkill — `InMemoryEmbeddingStore` would have worked. But Qdrant demonstrates the production-ready architecture pattern.

#### Future Risks

- **Qdrant client/server version coupling**: We already hit this — Qdrant v1.17.0 server returns protobuf formats that `io.qdrant:client:1.11.0` (bundled in LangChain4j 0.35.0) can't read. Must pin server version to match bundled client.
- **Operational overhead**: Running a separate Docker container in production requires orchestration (Kubernetes, etc.).

---

## Was There Really Any Workflow Change?

**No.** Despite the abstract mentioning "streamline operations with smart chatbots" and "optimize workflows with AI triggers," the actual implementation makes **zero workflow changes**:

1. **No existing code was modified**: Not a single line in any domain entity, repository, service, or existing UI page was changed.
2. **No chatbot**: No conversational UI or chat widget was implemented.
3. **No auto-fill**: No form auto-fill feature exists (the PDFBox dependency was added but never used).
4. **No workflow triggers**: No AI-driven automation or event triggers were added.
5. **No smart routing**: The cargo routing logic is unchanged.

What was actually built is a **read-only query overlay**: the RAG system reads from the existing JPA entities, indexes them into a vector database, and lets users ask questions. It's purely additive and purely informational. The existing CRUD workflows (booking, tracking, handling events, routing) are completely untouched.

### What This Means for the Talk

The honest story is: **Brian added a RAG feature that lets you ask natural language questions about cargo data.** This is a good, practical first step that demonstrates the "From CRUD to Cognitive" concept. The other features from the abstract (auto-fill, chatbot, workflow optimization) represent the vision but weren't implemented — they could be presented as "next steps" or "roadmap."

The architectural pattern (additive AI layer, CDI producers, LangChain4j abstractions, vector database) is solid and extensible. The talk can focus on:
1. How the AI layer was added without touching existing code
2. The RAG pattern (embed → search → augment → generate)
3. Technology choices and trade-offs
4. What it takes to go from CRUD to the first cognitive feature
5. Where you'd go next (the unrealized features from the abstract)
