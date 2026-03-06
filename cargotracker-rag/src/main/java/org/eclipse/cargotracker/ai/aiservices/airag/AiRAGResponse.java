package org.eclipse.cargotracker.ai.aiservices.airag;

import java.io.Serializable;
import java.util.List;

/**
 * Response model for RAG queries.
 * Contains the AI-generated answer and source citations.
 * 
 * @author Brian Benz & Ed Burns
 * @since 3.1
 */
public class AiRAGResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String answer;
    private List<String> sources;
    private String query;
    private int documentsRetrieved;
    
    public AiRAGResponse() {
    }
    
    public AiRAGResponse(String answer, List<String> sources, String query, int documentsRetrieved) {
        this.answer = answer;
        this.sources = sources;
        this.query = query;
        this.documentsRetrieved = documentsRetrieved;
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public List<String> getSources() {
        return sources;
    }
    
    public void setSources(List<String> sources) {
        this.sources = sources;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public int getDocumentsRetrieved() {
        return documentsRetrieved;
    }
    
    public void setDocumentsRetrieved(int documentsRetrieved) {
        this.documentsRetrieved = documentsRetrieved;
    }
}
