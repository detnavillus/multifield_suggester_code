package com.lucidworks.apollo.suggester;

import java.util.ArrayList;
import java.util.List;

public class MockSuggestionProcessor implements SuggestionProcessor {

  private ArrayList<QuerySuggestion> querySuggestions = new ArrayList<QuerySuggestion>( );
    
  @Override
  public void addSuggestion( QuerySuggestion suggestion ) {
    querySuggestions.add( suggestion );
  }
    
  @Override
  public void collectorDone( QueryCollector collector ) {
        
  }
    
  List<QuerySuggestion> getSuggestions(  ) {
    return querySuggestions;
  }
}