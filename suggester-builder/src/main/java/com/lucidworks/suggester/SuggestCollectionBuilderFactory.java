package com.lucidworks.suggester;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuggestCollectionBuilderFactory {
  private transient static final Logger LOG = LoggerFactory.getLogger( SuggestCollectionBuilderFactory.class );
    
  public static final String FILE_QUERY_COLLECTOR           = "FileQueryCollector";
  public static final String PIVOT_FACET_QUERY_COLLECTOR    = "PivotFacetQueryCollector";
  public static final String TERM_VECTOR_QUERY_COLLECTOR    = "TermVectorQueryCollector";

  public static SuggestCollectionBuilder createSuggestCollectionBuilder( String jsonConfig ) {
      
    SuggestCollectionBuilder suggestCollBuilder = new SuggestCollectionBuilder( );
      
    Map<String,Object> config = createConfig( jsonConfig );
    suggestCollBuilder.initialize( config );
      
    return suggestCollBuilder;
  }
    
  public static Map<String,Object> createConfig( String jsonConfig ) {
    // Use a JSON parse
    System.out.println( "createConfig:\n " + jsonConfig );
    Map<String,Object> configMap = parseConfig( jsonConfig );
    // get the queryCollectors - should be List<Object>
    if (configMap == null) {
      LOG.error( "Could not get Configuration! " );
      return null;
    }

    Object queryCollectors = configMap.get( "queryCollectors" );
    if (queryCollectors != null && queryCollectors instanceof List ) {
      List<QueryCollector> qCollectorList = convertQueryCollectorConfig(  (List<Map<String,Object>>)queryCollectors );
      configMap.put( "queryCollectors", qCollectorList );
    }
      
    return configMap;
  }
    
  static List<QueryCollector> convertQueryCollectorConfig( List<Map<String,Object>> collectorConfigs ) {
    LOG.debug( "convertQueryCollectorConfig ..." );
    ArrayList<QueryCollector> qCollectorList = new ArrayList<QueryCollector>( );
    for (Map<String,Object> qCollMap : collectorConfigs )
    {
      String type = (String)qCollMap.get( "type" );
      QueryCollector qCollector = createQueryCollector( type );
      qCollector.initialize( qCollMap );
      qCollectorList.add( qCollector );
    }
    return qCollectorList;
  }
    
  private static Map<String,Object> parseConfig( String jsonConfig )  {
    try {
      HashMap<String,Object> props = new ObjectMapper().readValue( jsonConfig, new TypeReference<HashMap<String,Object>>() {});
      return props;
    }
    catch ( IOException ioe ) {
      LOG.error( "Got Exception: " + ioe );
    }
    return null;
  }
    
  private static QueryCollector createQueryCollector( String type ) {
    if (type.equals( FILE_QUERY_COLLECTOR  )) {
      return new FileQueryCollector( );
    }
    else if (type.equals( PIVOT_FACET_QUERY_COLLECTOR )) {
      return new PivotFacetQueryCollector( );
    }
    else if (type.equals( TERM_VECTOR_QUERY_COLLECTOR )) {
      return new TermVectorQueryCollector( );
    }
      
    LOG.error( type + " Is not a valid QueryCollector type!" );
    return null;
  }
}
