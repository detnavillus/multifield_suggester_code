package com.lucidworks.suggester;

import java.util.List;
import java.util.Map;

public class QuerySuggestion {
  private String query;
    
  private Map<String,Object> metadata;
    
  private List<FieldValue> fieldValues;
    
  public QuerySuggestion( String query ) {
    this.query = query;
  }
    
  public QuerySuggestion( String query, Map<String,Object> metadata ) {
    this.query = query;
    this.metadata = metadata;
  }
    
  public QuerySuggestion( String query, Map<String,Object> metadata, List<FieldValue> fieldValues ) {
    this.query = query;
    this.metadata = metadata;
    this.fieldValues = fieldValues;
  }
  
  public String getQuery( ) {
    return this.query;
  }
    
  public List<FieldValue> getFieldValues( ) {
    return this.fieldValues;
  }
    
  public Map<String,Object> getMetadata( ) {
    return this.metadata;
  }
    

}