package com.lucidworks.suggester;

/**
 * Suggestion Processor Interface
 *
 * QueryCollectors should call collectorDone( ) when they have finished. This enables the
 * SuggestionProcessor to clean up, etc.
 */

public interface SuggestionProcessor {
    
  public void addSuggestion( QuerySuggestion suggestion );
    
  public void collectorDone( QueryCollector collector );
}