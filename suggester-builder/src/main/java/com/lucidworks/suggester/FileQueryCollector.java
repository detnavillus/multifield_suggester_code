package com.lucidworks.suggester;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileQueryCollector implements QueryCollector {
  private transient static final Logger LOG = LoggerFactory.getLogger( FileQueryCollector.class );
    
  public static final String QUERY_FIELD = "_QUERY_";
    
  private String fileName;
  private ArrayList<String> fields;
  private String fieldSeparator;
    
  private SuggestionProcessor suggestProcessor;
    
  public FileQueryCollector( )
  {
        
  }
    
  public FileQueryCollector( String fileName )
  {
    this.fileName = fileName;
  }

  public FileQueryCollector( String fileName, ArrayList<String> fields, String fieldSeparator )
  {
    this.fileName = fileName;
    this.fields = new ArrayList<String>( );
    this.fields.addAll( fields );
    this.fieldSeparator = fieldSeparator;
  }

  @Override
  public void initialize( Map<String,Object> config ) {
    Object fileNameOb = config.get( "fileName" );
    this.fileName = (fileNameOb != null) ? String.valueOf( fileNameOb ) : null;
  }
    
  public void run(  ) {
    try {
      InputStream is = null;
        
      File file = new File( fileName );
      if (file.isAbsolute()) {
        is = new FileInputStream( file );
      }
      else {
        is = FileQueryCollector.class.getClassLoader( ).getResourceAsStream( fileName );
      }
        
      BufferedReader br = new BufferedReader( new InputStreamReader( is ));
        
      String query = null;
      while ((query = br.readLine( )) != null) {
        HashMap<String,Object> meta = new HashMap<String,Object>( );
        meta.put( "fileName", fileName );
        meta.put( "collector", "FileQueryCollector" );
          
        if (fieldSeparator != null && fields != null) {
          String[] fieldVals = query.split( fieldSeparator );
          for (int i = 0; i < fields.size() && i < fieldVals.length; i++) {
            String fieldName = fields.get( i );
            if (fieldName.equals( QUERY_FIELD)) {
              query = fieldVals[i];
            }
            else {
              meta.put( fieldName, fieldVals[i] );
            }
          }
        }
          
        QuerySuggestion qSuggestion = new QuerySuggestion( query, meta, null );
        suggestProcessor.addSuggestion( qSuggestion );
      }
        
      br.close( );
    }
    catch ( IOException ioe ) {
          
    }
    finally {
      suggestProcessor.collectorDone( this );
    }
  }
    
  @Override
  public void setSuggestionProcessor( SuggestionProcessor suggestProcessor ) {
    LOG.debug( "setsuggestionProcessor" );
    this.suggestProcessor = suggestProcessor;
  }

}