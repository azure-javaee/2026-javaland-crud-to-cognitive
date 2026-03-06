# Demo steps

1. Open three shells, and do the commands in this order.

   1. One for qdrant.
   
      ```
      docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.11.5
      docker ps | grep qdrant
      ```
      
      Should show something like
      
      ```
      f37ac7d5e849   qdrant/qdrant:v1.11.5   "./entrypoint.sh"   33 minutes ago   Up 33 minutes   0.0.0.0:6333-6334->6333-6334/tcp, [::]:6333-6334->6333-6334/tcp   cranky_archimedes
      ```
      
      ```
      curl http://localhost:6333
      ```
      
      Should show something like
      
      ```
      {"title":"qdrant - vector search engine","version":"1.11.5","commit":"dee106d74625e891256ebe06c2c2e0515650e67e"}
      ```
      
   1. One for Liberty.
   
      Change the text in angle brackets to suit your environment when executing these commands.
   
      ```
      ./mvnw clean package -Popenliberty -DskipTests
      source .env.<your env file>
      ./mvnw liberty:run -Popenliberty > <YYYYMMDD>-liberty-<NN>.log 2>&1
      ```
      
      This command may also be useful:
      
      ```
      tail -f target/liberty/wlp/usr/servers/cargo-tracker/logs/messages.log | grep -E "🚀|✅|❌|AiStartup|Qdrant|indexing"
      ```

   1. One for interacting with the system via cURL.
   
      ```
      curl -X POST http://localhost:8080/cargo-tracker/rest/ai/rag/reindex
      ```

      Should show something like
      
      ```
      {"indexed":true,"message":"Historical data re-indexing started"}
      ```

1. Open the browser to cargo tracker

   http://localhost:8080/cargo-tracker
   
   If desired, interact with Cargo Tracker according to the steps at [this appendix](https://github.com/Azure-Samples/cargotracker-liberty-aks?tab=readme-ov-file#appendix-1---exercise-cargo-tracker-functionality).
   
## Here are some questions you can ask

**Setup**:

1. Visit the RAG page at http://localhost:8080/cargo-tracker/ai-rag.xhtml

**Steps**:

- What handling events occurred in New York?
   — Should find UNLOAD events for ABC123 and JKL567 at New York, plus the LOAD events for JKL567 (wrong voyage) and MNO456.

- What is the complete handling history for cargo MNO456?
   — Should return all 5 events: RECEIVE, LOAD, UNLOAD, CUSTOMS, CLAIM in Dallas.

- Has any cargo been through customs?
   — Should identify MNO456's CUSTOMS event in Dallas.

- Which cargo has been claimed by the recipient?
   — Should find MNO456's CLAIM event.

- What cargo was received in Hong Kong?
   — Should find ABC123's RECEIVE event.

- Show me all cargo currently in transit.
   — Should surface ABC123 (IN_PORT at New York based on delivery status).
   
- What cargo went to Helsinki?
   - ABC123 is destined for Helsinki

- Where is shipment JKL567
   - JKL567 was last recorded as being loaded in New York on 2026-03-02
   
**Talking Points**:
- "This searches 12 cargo records in our demo"
- "Scales to millions without code changes"
- "Sources are cited for transparency"
- "3-second response time including AI processing"

## Troubleshooting

### Collection Not Created

```bash
# Check Qdrant is running
docker ps | grep qdrant

# Check logs for errors
grep -i "qdrant\|collection" target/liberty/wlp/usr/servers/cargo-tracker/logs/messages.log

# Manually create collection
curl -X PUT "http://localhost:6333/collections/cargo_tracker_history" \
  -H "Content-Type: application/json" \
  -d '{"vectors": {"size": 1536, "distance": "Cosine"}}'
```

### No AI Logs Appearing

This was the original problem - beans weren't instantiating. `AiStartupInitializer` fixes this.

If you still don't see logs:
1. Check `src/main/webapp/WEB-INF/beans.xml` exists
2. Verify `AiStartupInitializer.class` is in WAR: `jar -tf target/cargo-tracker.war | grep AiStartupInitializer`
3. Check server.xml has AI libraries: `<library id="aiLib">`

### Data Not Indexing

```bash
# Check if indexer bean is loaded
grep "AiHistoricalIndexer" target/liberty/wlp/usr/servers/cargo-tracker/logs/messages.log

# Check database has cargo records
# (Access H2 console or check via application)

# Manually trigger reindex by restarting application
./mvnw liberty:stop -Popenliberty
./mvnw liberty:run -Popenliberty
```

### Azure OpenAI Errors

```bash
# Verify environment variables
env | grep AZURE_OPENAI

# Test endpoint manually
curl -H "api-key: $AZURE_OPENAI_KEY" \
     "$AZURE_OPENAI_ENDPOINT/openai/deployments?api-version=2023-05-15"

# Check deployment names match
curl -H "api-key: $AZURE_OPENAI_KEY" \
     "$AZURE_OPENAI_ENDPOINT/openai/deployments/$AZURE_OPENAI_DEPLOYMENT_NAME?api-version=2023-05-15"
```

### Issue 1: "Collection doesn't exist"

**Symptoms**: Error when querying RAG endpoint

**Solution**:
```bash
# Verify Qdrant is running
docker ps | grep qdrant

# Check collection exists
curl http://localhost:6333/collections/cargo_tracker_history

# If missing, restart Liberty (will auto-create)
./mvnw liberty:stop -Popenliberty
./mvnw liberty:run -Popenliberty
```

### Issue 2: "Azure OpenAI rate limit exceeded"

**Symptoms**: 429 HTTP errors

**Solution**:
- Check Azure Portal → OpenAI → Quotas
- Implement retry logic with exponential backoff
- Consider caching common queries
- Upgrade to higher-tier subscription

### Issue 3: "Poor RAG answer quality"

**Symptoms**: Irrelevant or incorrect answers

**Solution**:
- Check indexed data quality (garbage in, garbage out)
- Adjust chunk sizes (currently 500 characters)
- Tune similarity threshold (currently top 5 matches)
- Add metadata filters (date ranges, categories)
- Improve prompts (add examples, constraints)

### Issue 4: "Slow response times"

**Symptoms**: >10 second query times

**Solution**:
- Enable caching (Redis, in-memory)
- Use smaller embedding model (Ada-002 is already optimal)
- Reduce context size (fewer search results)
- Consider GPT-3.5 for simple queries (10x faster, 1/10 cost)

---
