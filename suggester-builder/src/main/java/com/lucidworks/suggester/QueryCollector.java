package com.lucidworks.suggester;

import java.util.List;
import java.util.Map;

public interface QueryCollector extends Runnable {
    
  public void initialize( Map<String,Object> config );
    
  public void setSuggestionProcessor( SuggestionProcessor processor );
}