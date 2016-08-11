package com.lucidworks.apollo.suggester;

import java.util.List;

import junit.framework.TestCase;

public class TestFileQueryCollector extends TestCase {
    
  public void testFileQueryCollector( ) {
    MockSuggestionProcessor sugProc = new MockSuggestionProcessor( );
    FileQueryCollector fileQC = new FileQueryCollector( "TestFileQueryCollector/testQueries.txt" );
    fileQC.setSuggestionProcessor( sugProc );
      
    fileQC.run( );
      
    List<QuerySuggestion> suggestions = sugProc.getSuggestions( );
    System.out.println( "Got " + ((suggestions != null) ? suggestions.size( ) : 0) + " suggestions!" );
    for (QuerySuggestion suggestion: suggestions ) {
      System.out.println( suggestion.getQuery( ) );
    }
  }
}