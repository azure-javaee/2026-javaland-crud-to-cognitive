# Data Flow: Shortest Path REST Endpoint

## Request Flow (AI Path — Happy Path)

```
 ┌──────────┐         ┌─────────────────────────────────────────────────────────────────────┐          ┌─────────────────────┐
 │          │  HTTP   │                        OPEN LIBERTY                                 │  HTTPS   │   AZURE OPENAI      │
 │  CLIENT  │ ──────> │                                                                     │ ───────> │   SERVICE           │
 │ (Browser │ GET     │  ┌──────────────────────┐    ┌─────────────────────┐                │          │                     │
 │  or REST │ /graph- │  │ GraphTraversalService│    │ ShortestPathService │                │          │  ┌───────────────┐  │
 │  client) │ traversal│ │ (JAX-RS @Stateless)  │    │ (JAX-RS @Path       │                │          │  │   GPT-4o      │  │
 │          │ /shortest│ │                      │    │  "/path")           │                │          │  │   Model       │  │
 │          │ -path?  │  │                      │    │                     │                │          │  │               │  │
 │          │ origin= │  │ 1. Check env vars:   │    │ Holds CSV data:     │                │          │  │  Processes    │  │
 │          │ SESTO&  │  │    AZURE_OPENAI_     │    │  - location.csv     │                │          │  │  ~90-line     │  │
 │          │ dest=   │  │    ENDPOINT          │    │  - voyage.csv       │                │          │  │  system       │  │
 │          │ NLRTM   │  │    AZURE_OPENAI_KEY  │    │  - carrier_movement │                │          │  │  prompt +     │  │
 │          │         │  │                      │    │    .csv             │                │          │  │  CSV data     │  │
 │          │         │  │ 2. If present:       │    │                     │                │          │  │               │  │
 │          │         │  │    call getShort-    │───>│ 3. getShortestPath  │                │          │  │  Returns      │  │
 │          │         │  │    estPathWith-      │    │    (from, to)       │                │          │  │  JSON path    │  │
 │          │         │  │    Timeout()         │    │                     │                │          │  │               │  │
 │          │         │  │    (2 min timeout)   │    │ 4. Calls            │                │          │  └───────────────┘  │
 │          │         │  │                      │    │    shortestPathAi   │                │          │                     │
 │          │         │  │                      │    │    .chat(location,  │                │          │                     │
 │          │         │  │                      │    │     voyage,         │                │          │                     │
 │          │         │  │                      │    │     carrier_movement│                │          │                     │
 │          │         │  │                      │    │     from, to)       │                │          │                     │
 │          │         │  │                      │    │         │           │                │          │                     │
 │          │         │  │                      │    │         ▼           │                │          │                     │
 │          │         │  │                      │    │ ┌───────────────┐   │   HTTP POST    │          │                     │
 │          │         │  │                      │    │ │ShortestPathAi │   │   (langchain4j │          │                     │
 │          │         │  │                      │    │ │ (interface)   │   │    handles)    │          │                     │
 │          │         │  │                      │    │ │               │──────────────────────────────>│                     │
 │          │         │  │                      │    │ │ @SystemMessage│   │                │          │                     │
 │          │         │  │                      │    │ │ @UserMessage  │   │                │          │                     │
 │          │         │  │                      │    │ └───────────────┘   │                │          │                     │
 │          │         │  │                      │    │         │           │                │          │                     │
 │          │         │  │                      │    │         ▼           │                │          │                     │
 │          │         │  │                      │    │ ┌─────────────────┐ │                │          │                     │
 │          │         │  │                      │    │ │ShortestPathAi-  │ │                │          │                     │
 │          │         │  │                      │    │ │Impl             │ │                │          │                     │
 │          │         │  │                      │    │ │                 │ │                │          │                     │
 │          │         │  │                      │    │ │AzureOpenAi-     │<────────────────────────────│                     │
 │          │         │  │                      │    │ │ChatModel        │ │  JSON response │          │                     │
 │          │         │  │                      │    │ └─────────────────┘ │                │          │                     │
 │          │         │  │                      │<───│ 5. Return JSON      │                │          │                     │
 │          │         │  │                      │    │    string           │                │          │                     │
 │          │         │  │ 6. Validate JSON     │    └─────────────────────┘                │          │                     │
 │          │         │  │ 7. Deserialize to    │                                           │          │                     │
 │          │         │  │    List<TransitPath>  │                                          │          │                     │
 │          │ <────── │  │ 8. Return response   │                                           │          │                     │
 │          │ JSON    │  └──────────────────────┘                                           │          │                     │
 │          │         │                                                                     │          │                     │
 └──────────┘         └─────────────────────────────────────────────────────────────────────┘          └─────────────────────┘
```

## Request Flow (Fallback Path — No AI)

```
 ┌──────────┐           ┌─────────────────────────────────────────────────────┐
 │          │  HTTP     │                    OPEN LIBERTY                     │
 │  CLIENT  │ ──────>   │                                                     │
 │          │ GET       │  ┌──────────────────────┐    ┌──────────┐           │
 │          │ /graph-   │  │ GraphTraversalService│    │ GraphDao │           │
 │          │ traversal │  │                      │    │          │           │
 │          │ /shortest │  │ 1. Check env vars:   │    │ In-memory│           │
 │          │ -path     │  │    MISSING or EMPTY  │    │ location │           │
 │          │           │  │                      │    │ data     │           │
 │          │           │  │ 2. Skip AI path      │    │          │           │
 │          │           │  │                      │    │          │           │
 │          │           │  │ 3. dao.listLocations │──> │          │           │
 │          │           │  │                      │<── │ locations│           │
 │          │           │  │                      │    │          │           │
 │          │           │  │ 4. Random shuffle    │    │          │           │
 │          │           │  │ 5. Build random      │    │          │           │
 │          │           │  │    TransitEdge list  │    │          │           │
 │          │           │  │ 6. Wrap in           │    │          │           │
 │          │ <──────   │  │    List<TransitPath> │    │          │           │
 │          │ JSON      │  └──────────────────────┘    └──────────┘           │
 │          │           │                                                     │
 └──────────┘           └─────────────────────────────────────────────────────┘
```

## Sequence Diagram (AI Path)

```
CLIENT              GraphTraversalService    ShortestPathService    ShortestPathAi/Impl     Azure OpenAI (GPT-4o)
  │                         │                        │                      │                        │
  │ GET /shortest-path      │                        │                      │                        │
  │ ?origin=SESTO           │                        │                      │                        │
  │ &destination=NLRTM      │                        │                      │                        │
  │────────────────────────>│                        │                      │                        │
  │                         │                        │                      │                        │
  │                         │ check AZURE_OPENAI_*   │                      │                        │
  │                         │ env vars (present)     │                      │                        │
  │                         │                        │                      │                        │
  │                         │ CompletableFuture      │                      │                        │
  │                         │ (2 min timeout)        │                      │                        │
  │                         │───────────────────────>│                      │                        │
  │                         │ getShortestPath        │                      │                        │
  │                         │ ("SESTO","NLRTM")      │                      │                        │
  │                         │                        │                      │                        │
  │                         │                        │ chat(location.csv,   │                        │
  │                         │                        │   voyage.csv,        │                        │
  │                         │                        │   carrier_movement   │                        │
  │                         │                        │   .csv, "SESTO",     │                        │
  │                         │                        │   "NLRTM")           │                        │
  │                         │                        │─────────────────────>│                        │
  │                         │                        │                      │                        │
  │                         │                        │                      │ Build prompt:          │
  │                         │                        │                      │ @SystemMessage (90     │
  │                         │                        │                      │   lines + CSV data)    │
  │                         │                        │                      │ @UserMessage           │
  │                         │                        │                      │ ("find shortest path   │
  │                         │                        │                      │  from SESTO to NLRTM") │
  │                         │                        │                      │                        │
  │                         │                        │                      │ POST /openai/          │
  │                         │                        │                      │ deployments/gpt-4o/    │
  │                         │                        │                      │ chat/completions       │
  │                         │                        │                      │───────────────────────>│
  │                         │                        │                      │                        │
  │                         │                        │                      │                        │ Process prompt,
  │                         │                        │                      │                        │ analyze CSV,
  │                         │                        │                      │                        │ compute shortest
  │                         │                        │                      │                        │ path
  │                         │                        │                      │                        │
  │                         │                        │                      │    JSON response       │
  │                         │                        │                      │<───────────────────────│
  │                         │                        │                      │ [{transitEdges: [...]}]│
  │                         │                        │                      │                        │
  │                         │                        │  String (JSON)       │                        │
  │                         │                        │<─────────────────────│                        │
  │                         │                        │                      │                        │
  │                         │ String (JSON)          │                      │                        │
  │                         │<───────────────────────│                      │                        │
  │                         │                        │                      │                        │
  │                         │ validate JSON          │                      │                        │
  │                         │ (isValidJsonUsingJsonP)│                      │                        │
  │                         │                        │                      │                        │
  │                         │ deserialize to         │                      │                        │
  │                         │ List<TransitPath>      │                      │                        │
  │                         │ (JSON-B)               │                      │                        │
  │                         │                        │                      │                        │
  │  List<TransitPath>      │                        │                      │                        │
  │  (JSON response)        │                        │                      │                        │
  │<────────────────────────│                        │                      │                        │
  │                         │                        │                      │                        │
```

## Key Classes in Liberty

```
┌──────────────────────────────────────────────────────────────────┐
│                        OPEN LIBERTY                              │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │ JAX-RS Layer                                            │     │
│  │                                                         │     │
│  │  GraphTraversalService (@Path "/graph-traversal")       │     │
│  │  ├── findShortestPath() — main entry point              │     │
│  │  ├── getShortestPathWithTimeout() — async + 2min timeout│     │
│  │  ├── isValidJsonUsingJsonP() — validates AI response    │     │
│  │  └── [fallback: random route generation]                │     │
│  │                                                         │     │
│  │  ShortestPathService (@Path "/path")                    │     │
│  │  ├── getShortestPath(from, to)                          │     │
│  │  ├── Loads: location.csv, voyage.csv,                   │     │
│  │  │         carrier_movement.csv                         │     │
│  │  └── Delegates to ShortestPathAi.chat()                 │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │ AI Service Layer (langchain4j)                          │     │
│  │                                                         │     │
│  │  ShortestPathAi (interface)                             │     │
│  │  ├── @SystemMessage — 90-line prompt with CSV templates │     │
│  │  ├── @UserMessage — "find shortest path from X to Y"    │     │
│  │  └── chat(location, voyage, carrier_movement, from, to) │     │
│  │                                                         │     │
│  │  ShortestPathAiImpl (@ApplicationScoped)                │     │
│  │  ├── AzureOpenAiChatModel (static, temp=0.2)            │     │
│  │  └── AiServices.builder(ShortestPathAi.class)           │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │ Domain Layer (unchanged from original)                  │     │
│  │                                                         │     │
│  │  TransitPath — list of TransitEdge objects              │     │
│  │  TransitEdge — from/to locations, dates, voyage number  │     │
│  │  GraphDao — in-memory location/voyage data              │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

## Data Composition: What Goes Into the LLM Prompt

```
┌─────────────────────────────────────────────────────────┐
│              PROMPT SENT TO GPT-4o                      │
│                                                         │
│  ┌───────────────────────────────────────────────┐      │
│  │ @SystemMessage (~90 lines)                    │      │
│  │                                               │      │
│  │  Role: "expert in finding shortest paths"     │      │
│  │  Rules: chronological order, field mappings   │      │
│  │  Output format: JSON array specification      │      │
│  │  Example: worked SESTO→NLRTM path             │      │
│  │                                               │      │
│  │  ┌─────────────┐ ┌──────────┐ ┌────────────┐  │      │
│  │  │location.csv │ │voyage.csv│ │carrier_    │  │      │
│  │  │             │ │          │ │movement.csv│  │      │
│  │  │13 locations │ │5 voyages │ │13 movements│  │      │
│  │  │with IDs and │ │with IDs  │ │with times, │  │      │
│  │  │UNLOCODEs    │ │& numbers │ │locations,  │  │      │
│  │  │             │ │          │ │voyages     │  │      │
│  │  └─────────────┘ └──────────┘ └────────────┘  │      │
│  └───────────────────────────────────────────────┘      │
│                                                         │
│  ┌───────────────────────────────────────────────┐      │
│  │ @UserMessage                                  │      │
│  │ "Please help me find the shortest path        │      │
│  │  from SESTO to NLRTM"                         │      │
│  └───────────────────────────────────────────────┘      │
│                                                         │
└─────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│              GPT-4o RESPONSE                            │
│                                                         │
│  [                                                      │
│    {                                                    │
│      "transitEdges": [                                  │
│        {                                                │
│          "fromDate": "2024-08-15T17:32:15.00000000",    │
│          "fromUnLocode": "SESTO",                       │
│          "toDate": "2024-08-15T19:47:15.00000000",      │
│          "toUnLocode": "FIHEL",                         │
│          "voyageNumber": "0300A"                        │
│        },                                               │
│        {                                                │
│          "fromDate": "2024-08-17T14:22:15.00000000",    │
│          "fromUnLocode": "FIHEL",                       │
│          "toDate": "2024-08-19T22:42:15.00000000",      │
│          "toUnLocode": "NLRTM",                         │
│          "voyageNumber": "0400S"                        │
│        }                                                │
│      ]                                                  │
│    }                                                    │
│  ]                                                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```
