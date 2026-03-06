package org.eclipse.cargotracker.interfaces.backing;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.cargotracker.ai.aiservices.airag.AiRAGQueryService;
import org.eclipse.cargotracker.ai.aiservices.airag.AiRAGResponse;

import java.io.Serializable;

/**
 * JSF Backing Bean for RAG (Retrieval-Augmented Generation) queries.
 * 
 * Provides UI support for querying historical cargo data
 * using natural language.
 * 
 * @author Brian Benz & Ed Burns
 * @since 3.1
 */
@Named
@RequestScoped
public class AiRAGBean implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Inject
    private AiRAGQueryService ragQueryService;
    
    private String query;
    private AiRAGResponse response;
    private boolean loading;
    
    @PostConstruct
    public void init() {
        this.loading = false;
    }
    
    /**
     * Submit RAG query.
     */
    public void submitQuery() {
        if (query == null || query.trim().isEmpty()) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, 
                    "Query Required", 
                    "Please enter a question about cargo shipments."));
            return;
        }
        
        loading = true;
        
        try {
            response = ragQueryService.query(query);
            
            if (response != null && response.getDocumentsRetrieved() > 0) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                        "Query Processed",
                        "Found " + response.getDocumentsRetrieved() + " relevant documents."));
            }
            
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Query Failed",
                    "Error processing query: " + e.getMessage()));
            e.printStackTrace();
        } finally {
            loading = false;
        }
    }
    
    /**
     * Clear query and results.
     */
    public void clear() {
        query = null;
        response = null;
    }
    
    /**
     * Check if RAG service is available.
     */
    public boolean isServiceAvailable() {
        return ragQueryService != null && ragQueryService.isAvailable();
    }
    
    // Getters and Setters
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public AiRAGResponse getResponse() {
        return response;
    }
    
    public boolean isLoading() {
        return loading;
    }
    
    public boolean isResponseAvailable() {
        return response != null;
    }
}
