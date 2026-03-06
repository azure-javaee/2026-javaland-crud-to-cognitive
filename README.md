# From CRUD to Cognitive: Modernizing Legacy Java Apps with Embedded AI

This repository contains two demos, each showing different ways to add AI to an existing CRUD app.

- cargotracker-rag

   Add RAG over the routes with conversational access.
   
   
- cargotracker-ai-shortest-path

   Use AI to compute the shortest path between two ports, instead of the existing approach which generates a random route.
   
## Common set up

### Prerequisites

- Java 21 or higher
- Docker (for Qdrant vector database)
- Azure OpenAI subscription with API key
- Maven (included via wrapper: `./mvnw`)

### Model deployments

Both demos use langchain4j as the abstraction layer between the app and the AI. As such, an arbitrary variety of models can be used. This demo was developed and tested using models easily available at Microsoft Foundry (formerly known as Azure AI Studio).

The steps in this section show the common AI setup for both demos.

1. Sign in to https://ai.azure.com with your Visual Studio Enterprise credentials.

1. If necessary, create a new Project. For example, previously, this was created:

   | Name | Parent AI Foundry resource | Subscription | Resource group | Region |
   |------|---------------------------|--------------|----------------|--------|
   | 2026-jl-ct | 2026-jl-ct-resource | Visual Studio Enterprise | rg-2026-jl-ct | eastus2 |

1. Deploy two models.

   1. On the screen that shows **Start building**, select the drop down and select **Browse models**.
   
   1. Deploy a **gpt-4o** with Default config.
   
      Ensure the name is **gpt-4o** .
   
   1. Deploy a **text-embedding-3-small** with Default config.

      Ensure the name is **text-embedding-3-small**.
      
1. In the navigation bar, select **Home**.

1. For each demo, update the `.env.example` with these common environment variable decrarations.

   - `AZURE_OPENAI_ENDPOINT` is the value of the **Azure OpenAI endpoint** but **REMOVE THE trailing `openai/v1**.
   
   - `AZURE_OPENAI_KEY` is the value of the **Project API key**.
   
   ```
   export AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
   export AZURE_OPENAI_EMBEDDING_DEPLOYMENT=text-embedding-3-small
   ```

