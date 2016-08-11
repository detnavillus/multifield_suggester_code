package com.lucidworks.suggester;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SuggesterBuilder
{
  public static void main( String[] args ) {
    // create a suggester using a JSON configuration file
      
    if (args.length == 0) {
      System.out.println( "Usage: SuggesterBuilder <JSON Configuration File>" );
      return;
    }
      
    String configFile = args[0];
    SuggestCollectionBuilder suggestBuilder = SuggestCollectionBuilderFactory.createSuggestCollectionBuilder( readFile( configFile ) );
      
    suggestBuilder.buildCollection( );
  }
    
  private static String readFile( String dataFile ) {
    StringBuilder strb = new StringBuilder( );
    try {
      InputStream is = SuggesterBuilder.class.getClassLoader( ).getResourceAsStream( dataFile );
      BufferedReader br = new BufferedReader( new InputStreamReader( is ) );
      String line = null;
      while ((line = br.readLine( ) ) != null) {
        strb.append( line ).append( "\n" );
      }
      br.close( );
    }
    catch ( IOException ioe ) {
      System.out.println( "Got IOException: " + ioe );
    }
        
    return strb.toString( );
  }

}