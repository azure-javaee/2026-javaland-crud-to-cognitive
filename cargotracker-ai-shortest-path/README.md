# Demo steps

## Running locally

This section describes how to run Cargo Tracker locally on Open Liberty with an embedded HSQLDB database. The only external service required is Azure OpenAI (for the AI shortest path feature).

### Prerequisites

- JDK 17 or later
- Maven 3.8+ (the project includes a Maven wrapper `./mvnw`)

### 1. Create your environment file

Create a file named `.env.<your-username>` in the project root (e.g. `.env.edburns`). This file is git-ignored and will not be committed. Populate it with your Azure OpenAI credentials:

```bash
export AZURE_OPENAI_ENDPOINT=<your-azure-openai-endpoint>
export AZURE_OPENAI_KEY=<your-azure-openai-key>
export AZURE_OPENAI_DEPLOYMENT_NAME=<your-deployment-name>
export AZURE_OPENAI_CLIENT_ID=<your-client-id>
```

### 2. Source the environment file

```bash
source .env.<your-username>
```

### 3. Build the application

```bash
./mvnw clean package -Popenliberty-pathtraversal-local -DskipTests
```

### 4. Run the application

```bash
./mvnw liberty:run -Popenliberty-pathtraversal-local > YYYYMMDD-liberty-NN.log 2>&1
```

Wait until you see a message like:

```
[AUDIT] CWWKF0011I: The defaultServer server is ready to run a smarter planet.
```

### 5. Exercise the application

Open the Cargo Tracker UI in your browser:

```
http://localhost:9080/cargo-tracker/
```

Note the port is 9080.

Note the timestamp that shows when it was built.

To test the AI shortest path REST endpoint:

```bash
curl -s -X GET -H "Accept: application/json" "http://localhost:9080/cargo-tracker/rest/graph-traversal/shortest-path?origin=CNHKG&destination=USNYC" | jq .
```

You should receive a JSON response with transit edges representing the shortest path computed by Azure OpenAI.

### 6. Stop the server

Press `Ctrl+C` in the terminal where Liberty is running.
