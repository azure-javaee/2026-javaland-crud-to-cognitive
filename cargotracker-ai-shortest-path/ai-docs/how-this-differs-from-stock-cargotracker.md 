# From CRUD to Cognitive: What Zhihao Changed

## Overview

Zhihao took the Eclipse Cargo Tracker — a classic Domain-Driven Design (DDD) reference application running on Open Liberty — and added a GenAI-powered "shortest path" routing feature. The original app computed cargo routes using a **randomized algorithm** (shuffling locations and generating arbitrary transit edges). Zhihao replaced this with an **LLM-powered path finder** that uses Azure OpenAI (GPT-4o) to compute the actual shortest path based on real shipping data, with graceful fallback to the original randomized algorithm if the AI service is unavailable.

The key insight: **the existing REST API contract and domain model were preserved**. The same `/graph-traversal/shortest-path` endpoint, the same `TransitPath`/`TransitEdge` domain objects, the same UI. The AI capability was injected _behind_ the existing interface.

---

## What Was Added

### New Java Classes (3 files)

| Class | Role |
|-------|------|
| `ShortestPathAi` (interface) | Declarative AI service interface using langchain4j annotations (`@SystemMessage`, `@UserMessage`, `@V`) to define the LLM prompt and input variables |
| `ShortestPathAiImpl` | CDI bean that initializes `AzureOpenAiChatModel` and wraps the interface with `AiServices.builder()` |
| `ShortestPathService` | JAX-RS REST service (`@Path("/path")`) that loads CSV data from the classpath and delegates to `ShortestPathAi` |

### Modified Java Class (1 file)

| Class | What Changed |
|-------|-------------|
| `GraphTraversalService` | Added AI-first routing: checks for Azure OpenAI env vars → calls `ShortestPathService` with 2-minute timeout → validates JSON response → falls back to original randomized algorithm if AI is unavailable or returns bad data |

### New Data Files (3 files)

| File | Purpose |
|------|---------|
| `location.csv` | 13 port locations with IDs and UN/LOCODE codes — fed into the LLM prompt |
| `voyage.csv` | 5 voyages with IDs and voyage numbers — fed into the LLM prompt |
| `carrier_movement.csv` | 13 carrier movement records (edges in the shipping graph) — fed into the LLM prompt |

### Infrastructure (Azure Bicep)

| File | Purpose |
|------|---------|
| `cognitiveservices.bicep` | Provisions Azure Cognitive Services (OpenAI kind) with GPT-4o deployment (model version `2024-08-06`, Standard SKU, 20 capacity units) |

---

## Libraries Used

### langchain4j 0.34.0

**What it is:** A Java-native LLM integration framework, inspired by Python's LangChain. It provides a high-level abstraction for interacting with LLMs from Java code.

**What Zhihao used from it:**

1. **`dev.langchain4j:langchain4j`** (core) — The `AiServices` builder pattern and prompt annotations (`@SystemMessage`, `@UserMessage`, `@V` template variables).

2. **`dev.langchain4j:langchain4j-azure-open-ai`** (Azure connector) — The `AzureOpenAiChatModel` class for connecting to Azure OpenAI endpoints.

**How the prompt works:** The `@SystemMessage` annotation contains a ~90-line prompt that:
- Defines the AI's role ("expert in finding shortest paths")
- Provides CSV data as inline context (locations, voyages, carrier movements) via `{{variable}}` template syntax
- Specifies exact JSON output format with a worked example
- Sets strict constraints (chronological order, matching UNLOCODEs, no extra text)

This is essentially **in-context learning** — the LLM receives all the graph data in the prompt and must compute the shortest path at inference time.

---

## Frameworks Used

| Framework | Version | Role |
|-----------|---------|------|
| **Open Liberty** | Runtime | Jakarta EE application server (JAX-RS, CDI, EJB, JSON-B) |
| **langchain4j** | 0.34.0 | LLM integration framework — declarative AI service pattern |
| **Jakarta EE 10** | (via Liberty) | CDI for dependency injection, JAX-RS for REST, JSON-B for serialization |

---

## Technologies Used

| Technology | Role |
|------------|------|
| **Azure OpenAI Service** | Hosts the GPT-4o model used for path computation |
| **GPT-4o** (version 2024-08-06) | The specific LLM model deployed |
| **Azure Kubernetes Service (AKS)** | Runtime hosting for the containerized Liberty app |
| **Azure Database for PostgreSQL** | Replaced the original in-memory/Derby database |
| **Azure Application Insights** | Monitoring and telemetry |
| **Azure Bicep** | Infrastructure-as-Code for provisioning all Azure resources |

---

## Why These Technologies Were a Good Choice

### langchain4j

- **Java-native.** Unlike Spring AI (which requires Spring Boot), langchain4j works with plain Jakarta EE / CDI. This was critical since CargoTracker runs on Open Liberty with EJBs and JAX-RS — not Spring Boot.
- **Declarative AI services.** The `@SystemMessage` / `@UserMessage` annotation pattern is clean and keeps the prompt co-located with the interface contract. No boilerplate HTTP client code.
- **Minimal invasion.** Only 3 new classes were needed. The existing domain model and REST contracts were untouched.
- **Framework-agnostic.** langchain4j doesn't impose an application framework. It works with CDI, Spring, or plain Java.

### Azure OpenAI (GPT-4o)

- **Enterprise-grade.** Azure OpenAI provides the same models as OpenAI but with private networking, SLA guarantees, and data residency compliance — important for enterprise demos.
- **Managed deployment.** No need to self-host models. The Bicep template provisions everything.
- **Low temperature (0.2).** Appropriate for a deterministic task like path finding — reduces randomness in the LLM output.

### In-context learning (CSV in prompt) vs. RAG

- **Simplicity.** The entire graph fits in the prompt window (~13 locations, 13 edges, 5 voyages). No need for a vector database or embedding pipeline.
- **Demo-appropriate.** For a conference demo, this approach is fast to implement and easy to explain.

---

## What Other Technologies Could Have Been Used Instead?

| Alternative | Trade-offs |
|-------------|-----------|
| **Spring AI** | Requires Spring Boot. CargoTracker is a Jakarta EE app on Liberty, so Spring AI would have required a much larger refactoring effort. |
| **Semantic Kernel for Java** | Microsoft's SDK. More complex API surface; langchain4j's declarative annotations are simpler for this use case. |
| **Direct Azure OpenAI REST calls** | No framework dependency, but requires manual HTTP client code, prompt construction, and response parsing. More boilerplate. |
| **RAG with vector database** | Overkill for 13 locations. Would require embedding generation, a vector store (Azure AI Search, pgvector, etc.), and retrieval logic. Makes sense if the dataset were 10,000+ routes. |
| **Graph database (Neo4j)** | A classical shortest-path solution. Would give deterministic results with Dijkstra/BFS algorithms. No AI needed, but doesn't demonstrate the "CRUD to Cognitive" concept. |
| **OpenAI API directly** | Would work, but Azure OpenAI provides enterprise features (VNet integration, managed keys, compliance). For an Azure-focused demo, Azure OpenAI is the right choice. |

---

## What Problems Might This Technology Pose in the Future?

### 1. LLM Non-determinism
Even with `temperature=0.2`, GPT-4o can produce slightly different outputs on repeated calls. For a logistics application, routes should be deterministic and verifiable. A graph algorithm would always return the same shortest path.

### 2. Latency
The 2-minute timeout tells the story. An LLM call for path computation is orders of magnitude slower than an in-memory graph traversal. For production traffic, this latency is unacceptable.

### 3. Cost at Scale
Every route request sends ~5KB of CSV data plus a ~90-line system prompt to GPT-4o. At enterprise scale (thousands of routing requests/day), the token costs would be significant compared to a free in-memory algorithm.

### 4. Prompt Fragility
The 90-line system prompt is essentially business logic encoded in natural language. It specifies date formats, field mappings, JSON output schemas, and edge constraints. Any change to the data model requires updating the prose prompt — no compiler will catch errors.

### 5. Data Staleness
The CSV files are loaded from the classpath at startup. If the shipping graph changes (new routes, new ports), the CSV files and the prompt must be manually updated and redeployed. A database-backed solution would be more dynamic.

### 6. langchain4j Version Churn
langchain4j is a fast-evolving project (0.x versioning). APIs may break between versions. The `AzureOpenAiChatModel` builder API, the `@V` annotation, and the `AiServices` pattern are all subject to change.

### 7. Security: API Keys in Environment Variables
`ShortestPathAiImpl` reads `AZURE_OPENAI_KEY` from environment variables and initializes a static model instance. In production, consider Azure Managed Identity with `DefaultAzureCredential` instead of API keys.

---

## Was There Really Any Workflow Change?

**From the user's perspective: No.** The booking workflow — selecting origin, destination, deadline, and viewing route candidates — is identical. The UI buttons, forms, and flow are unchanged.

**From the system's perspective: Yes, but minimally.** The routing algorithm was replaced. Where the original code generated random routes by shuffling locations, the AI-enabled version asks GPT-4o to compute the actual shortest path based on real shipping schedules. The REST endpoint contract (`/graph-traversal/shortest-path`) and the response format (`List<TransitPath>`) are unchanged.

**What this demonstrates for the talk:** You can inject AI behind existing interfaces without disrupting the user experience or the system architecture. The "do something with AI" mandate doesn't require ripping apart existing systems — it requires finding the right seam (in this case, the routing algorithm) and replacing the implementation while preserving the contract.

### Summary of Workflow Impact

| Aspect | Before (Original) | After (AI-Enabled) |
|--------|-------------------|---------------------|
| Route computation | Random shuffle of locations | GPT-4o analyzes real shipping data |
| Response format | `List<TransitPath>` | `List<TransitPath>` (unchanged) |
| REST endpoint | `/graph-traversal/shortest-path` | `/graph-traversal/shortest-path` (unchanged) |
| Fallback behavior | N/A | Falls back to random algorithm if AI unavailable |
| User experience | Sees random route candidates | Sees optimized route (or random fallback) |
| New UI elements | N/A | None — same booking flow |
