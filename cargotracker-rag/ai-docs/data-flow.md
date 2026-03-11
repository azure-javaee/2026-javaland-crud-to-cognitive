# RAG Data Flow Diagrams

## 1. Indexing Flow (one-time, triggered by `POST /rest/ai/rag/reindex`)

```
┌──────────────┐
│   Operator   │
│  (curl/REST) │
└──────┬───────┘
       │ POST /rest/ai/rag/reindex
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Open Liberty (Java 21)                           │
│                                                                          │
│  ┌────────────────┐      ┌─────────────────────────────────────────────┐ │
│  │ AiRAGResource  │─────▶│       AiHistoricalIndexer.reindex()         │ │
│  │ (JAX-RS)       │      │                                             │ │
│  └────────────────┘      │ STEP 1: em.createNamedQuery("Cargo.findAll")│ │
│                          │          em.createQuery("SELECT e FROM      │ │
│                          │                         HandlingEvent e")   │ │
│                          │          │                                  │ │
│                          │          │ JPQL queries                     │ │
│                          │          ▼                                  │ │
│                          │  ┌─────────────────────┐                    │ │
│                          │  │ EntityManager (JPA) │                    │ │
│                          │  └──────────┬──────────┘                    │ │
│                          │             │                               │ │
│                          │             │ SQL to H2 ──────────────────────│─┐
│                          │             │                               │ │ │
│                          │             │ ◄── C Cargo + H Handling ───────│─┘
│                          │             │     Java objects              │ │
│                          │             ▼                               │ │
│                          │  STEP 2: For each entity, build a           │ │
│                          │          plain-text string:                 │ │
│                          │                                             │ │
│                          │    buildCargoDocument(cargo) ──▶            │ │
│                          │      "Cargo Tracking ID: ABC123,            │ │
│                          │       Origin: Hong Kong,                    │ │
│                          │       Destination: Helsinki..."             │ │
│                          │                                             │ │
│                          │    buildHandlingEventDocument(event) ──▶    │ │
│                          │      "Handling Event for Cargo: MNO456,     │ │
│                          │       Event Type: CUSTOMS,                  │ │
│                          │       Location: Dallas..."                  │ │
│                          │                                             │ │
│                          │    (These are NOT files — just Strings      │ │
│                          │     constructed from entity fields.)        │ │
│                          │                                             │ │
│                          │  STEP 3: Send each of the N text strings ─────│─┐
│                          │          to Azure for embedding             │ │ │
│                          │                                             │ │ │
│                          │          ◄── receive 1,536-float vector ──────│─┘
│                          │                                             │ │
│                          │  STEP 4: Store vector + original text ────────│─┐
│                          │          in Qdrant                          │ │ │
│                          │                                             │ │ │
│                          └─────────────────────────────────────────────┘ │ │
│                                                                          │ │
└──────────────────────────────────────────────────────────────────────────┘ │
                                                                         │ │ │
          ┌──────────────────────────────────────────────────────────────┘ │ │
          │                                                                │ │
          ▼                                                                │ │
┌─────────────────────┐                                                    │ │
│    H2 Database      │                                                    │ │
│   (in-memory)       │                                                    │ │
│                     │                                                    │ │
│  Cargo table:       │                                                    │ │
│    C rows           │                                                    │ │
│  HandlingEvent tbl: │                                                    │ │
│    H rows           │                                                    │ │
└─────────────────────┘                                                    │ │
                                                                           │ │
          ┌────────────────────────────────────────────────────────────────┘ │
          │                                                                  │
          ▼                                                                  │
┌───────────────────────────────────┐                                        │
│      Azure OpenAI Service         │                                        │
│   (text-embedding-3-small)        │                                        │
│                                   │                                        │
│  Called N times (once per text):  │                                        │
│                                   │                                        │
│  Input:  "Cargo Tracking ID:      │                                        │
│           ABC123, Origin: Hong    │                                        │
│           Kong, Destination:      │                                        │
│           Helsinki..."            │                                        │
│                                   │                                        │
│  Output: [0.023, -0.117, 0.842,   │                                        │
│           ... 1,536 floats]       │                                        │
└───────────────────────────────────┘                                        │
                                                                             │
          ┌──────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────┐
│        Qdrant (Docker)           │
│     localhost:6334 (gRPC)        │
│                                  │
│  Collection: cargo_tracker_      │
│              history             │
│                                  │
│  Point 1:                        │
│    vector: [0.023, -0.117, ...]  │
│    text: "Cargo Tracking ID:     │
│           ABC123..."             │
│    metadata: {trackingId:ABC123, │
│      type:cargo}                 │
│                                  │
│  ... (N points total)            │
│                                  │
│  Point N:                        │
│    vector: [0.056, 0.234, ...]   │
│    text: "Handling Event for     │
│           Cargo: MNO456,         │
│           Type: CLAIM..."        │
│    metadata: {trackingId:MNO456, │
│      type:handling_event}        │
└──────────────────────────────────┘

Result: N points stored (one per cargo + one per handling event)
```

## 2. Query Flow (per user question)

```
┌──────────────┐
│     User     │
│  (browser or │
│    curl)     │
└──────┬───────┘
       │ POST /rest/ai/rag/query
       │ {"query": "Has any cargo been through customs?"}
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Open Liberty (Java 21)                          │
│                                                                          │
│  ┌────────────────┐     ┌────────────────────────────────────────┐      │
│  │ AiRAGResource  │────▶│ AiRAGQueryService.query(question)      │      │
│  │ (JAX-RS)       │     └───────────────┬────────────────────────┘      │
│  └────────────────┘                     │                                │
│                                         │                                │
│         ┌───────────────────────────────┘                                │
│         │                                                                │
│         │  STEP 1: Embed the question                                    │
│         │                                                                │
│         │  embeddingModel.embed("Has any cargo been through customs?")   │
│         │                                                                │
└─────────┼────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────┐
│     Azure OpenAI Service         │
│   (text-embedding-3-small)       │
│                                  │
│ "Has any cargo been through      │
│  customs?"                       │
│         │                        │
│         ▼                        │
│  [0.089, -0.201, 0.445, ...]    │
│  (1,536 floats)                  │
└───────────────┬──────────────────┘
                │ query vector
                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Open Liberty (continued)                         │
│                                                                          │
│         STEP 2: Search Qdrant for similar vectors                        │
│                                                                          │
│         embeddingStore.findRelevant(queryVector, maxResults=5, minScore=0.7)│
│                                                                          │
└─────────┬────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────┐
│       Qdrant (Docker)            │
│    localhost:6334 (gRPC)         │
│                                  │
│  Compare query vector against    │
│  all N stored vectors using      │
│  cosine similarity:              │
│                                  │
│  Point 12 (MNO456 CUSTOMS):     │
│    cosine similarity = 0.76  ✓   │
│  Point 14 (MNO456 CLAIM):       │
│    cosine similarity = 0.75  ✓   │
│  Point  4 (MNO456 cargo):       │
│    cosine similarity = 0.75  ✓   │
│  Point  7 (JKL567 UNLOAD):      │
│    cosine similarity = 0.74  ✓   │
│  Point  1 (ABC123 cargo):       │
│    cosine similarity = 0.74  ✓   │
│  Point  9 (ABC123 RECEIVE):     │
│    cosine similarity = 0.68  ✗   │
│    (below 0.7 threshold)         │
│                                  │
│  Returns top 5 matches ≥ 0.7    │
└───────────────┬──────────────────┘
                │ 5 matching TextSegments
                │ (original text + metadata + scores)
                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Open Liberty (continued)                         │
│                                                                          │
│         STEP 3: Build augmented prompt                                   │
│                                                                          │
│         Combine retrieved documents + user question into one prompt:     │
│                                                                          │
│         ┌──────────────────────────────────────────────────────────┐     │
│         │ System: "You are a helpful cargo tracking assistant.     │     │
│         │          Use the provided context to answer questions    │     │
│         │          about cargo shipments accurately."              │     │
│         │                                                          │     │
│         │ User: "Context from historical cargo data:               │     │
│         │        Document 1:                                       │     │
│         │        Handling Event for Cargo: MNO456                  │     │
│         │        Event Type: CUSTOMS                               │     │
│         │        Location: Dallas                                  │     │
│         │        Completed: 2026-02-04T...                         │     │
│         │                                                          │     │
│         │        Document 2:                                       │     │
│         │        Handling Event for Cargo: MNO456                  │     │
│         │        Event Type: CLAIM                                 │     │
│         │        Location: Dallas                                  │     │
│         │        ...                                               │     │
│         │                                                          │     │
│         │        (3 more documents)                                │     │
│         │                                                          │     │
│         │        User Question: Has any cargo been through         │     │
│         │        customs?                                          │     │
│         │                                                          │     │
│         │        Please answer based on the context above."        │     │
│         └──────────────────────────────────────────────────────────┘     │
│                                                                          │
│         STEP 4: Send to GPT-4o                                           │
│                                                                          │
│         chatModel.generate(messages)                                     │
│                                                                          │
└─────────┬────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────┐
│     Azure OpenAI Service         │
│          (gpt-4o)                │
│                                  │
│  Reads the context documents     │
│  and the question.               │
│                                  │
│  Generates:                      │
│  "Based on the provided context, │
│   cargo with tracking ID MNO456  │
│   has been through customs.      │
│   Specifically, the cargo        │
│   underwent a CUSTOMS handling   │
│   event in Dallas, completed on  │
│   2026-02-04..."                 │
│                                  │
└───────────────┬──────────────────┘
                │ natural language answer
                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Open Liberty (continued)                         │
│                                                                          │
│         STEP 5: Package response                                         │
│                                                                          │
│         AiRAGResponse:                                                   │
│           answer: "Based on the provided context, cargo..."              │
│           sources: ["Cargo MNO456 (relevance: 0.76)",                    │
│                     "Cargo ABC123 (relevance: 0.75)", ...]               │
│           query: "Has any cargo been through customs?"                   │
│           documentsRetrieved: 5                                          │
│                                                                          │
└─────────┬────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────┐
│     User     │
│              │
│  {           │
│    "answer": "Based on the      │
│     provided context, cargo     │
│     with tracking ID MNO456     │
│     has been through customs.   │
│     ...",                       │
│    "sources": [...],            │
│    "documentsRetrieved": 5      │
│  }                              │
└─────────────────────────────────┘
```

## Summary: Who Talks to Whom

```
                          INDEXING                              QUERYING

                    ┌───────────┐                         ┌───────────┐
                    │ H2 (JPA)  │                         │   User    │
                    └─────┬─────┘                         └─────┬─────┘
                          │ entities                            │ question
                          ▼                                     ▼
                    ┌───────────┐                         ┌───────────┐
                    │  Liberty  │                         │  Liberty  │
                    └──┬─────┬──┘                         └──┬──┬──┬──┘
                       │     │                               │  │  │
              text     │     │ vectors+text        question  │  │  │ prompt+context
                       ▼     ▼                               ▼  │  ▼
                  ┌───────┐ ┌────────┐               ┌───────┐  │ ┌───────┐
                  │ Azure │ │ Qdrant │               │ Azure │  │ │ Azure │
                  │ embed │ │ store  │               │ embed │  │ │ GPT-4o│
                  └───────┘ └────────┘               └───────┘  │ └───┬───┘
                                                                │     │
                                                     query vec  │     │ answer
                                                                ▼     │
                                                          ┌────────┐  │
                                                          │ Qdrant │  │
                                                          │ search │  │
                                                          └────┬───┘  │
                                                               │      │
                                                          matched     │
                                                          texts  │    │
                                                               ▼      ▼
                                                          ┌───────────┐
                                                          │   User    │
                                                          └───────────┘

Azure calls:  N (1 per document, indexing)       2 per query (embed + generate)
Qdrant calls: 1 batch write (indexing)           1 search (querying)
```
