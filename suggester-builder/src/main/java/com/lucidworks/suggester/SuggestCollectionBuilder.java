package com.lucidworks.suggester;

import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.CommonParams;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.response.QueryResponse;

import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a Suggester Collection based on a set of QueryCollector(s)
 * For each query, it uses a facet field to determine what relationships the documents have
 * (Primary use case for this is entitlement fields or ACLs so that the query can be filtered so that
 * a user never gets a suggestion that will return 0 results for them)
 */

public class SuggestCollectionBuilder implements SuggestionProcessor {
  private transient static final Logger LOG = LoggerFactory.getLogger( SuggestCollectionBuilder.class );
    
  public static final String NAME = "name";
  public static final String SOURCE_ZK_HOSTS  = "sourceZKHosts";
  public static final String SUGGEST_ZK_HOSTS = "suggestZKHosts";
  public static final String SOURCE_HOST = "sourceHost";
  public static final String QUERY_SOURCE_FIELD = "querySourceField";
  public static final String SOURCE_HANDLER_FIELD = "sourceHandler";
  public static final String SUGGEST_HOST = "suggestHost";
  public static final String QUERY_SUGGEST_FIELD = "querySuggestField";
  public static final String CONTENT_COLLECTION = "contentCollection";
  public static final String SUGGESTER_COLLECTION = "suggesterCollection";
  public static final String QUERY_COLLECTORS = "queryCollectors";
  public static final String SOURCE_COLLECTION_FIELD = "sourceCollectionField";
    
  private static int BATCH_SIZE = 1000;
    
  private String name;
    
  private String sourceHost;
  private List<String> sourceZkHosts;
  private SolrClient sourceClient;
    
  private String suggestHost;
  private List<String> suggestZkHosts;
  private SolrClient suggestClient;
    
  private String contentCollection;
  private String suggesterCollection;
    
  private List<QueryCollector> queryCollectors;
  private Map<String,String> facetFieldMap;     // map of facet field name -> suggester field name
  private Map<String,String> metaFieldMap;      // map of suggestion meta field -> suggester field name
    
  private String sourceCollectionField;        // suggester field to put the source collection name
    
  private String querySuggestField;              // field in suggest collection that contains the query string.
  private String querySourceField = null;        // field in content collection that should be searched for hits
  private String sourceHandler = "/select";
    
  private String fieldValuesField = "fieldValues_ss";
    
  // comprehensive facet context field
  // list of fields that will be considered
  // number of facet values per field OR minimum threshold
  private FacetContextField facetContextField;
    
  private int batchSize = BATCH_SIZE;
    
  private int nCollectors = 0;

  private int nSuggestions = 0;
    
  private ArrayList<SolrInputDocument> queryDocs = new ArrayList<SolrInputDocument>( );
  private ArrayList<SolrInputDocument> deduplicatedDocs;
    
  private boolean addCounts = false;
    
  private boolean deduplicate = true;
    
  private HashMap<String, SolrInputDocument> suggestions;
    
  public SuggestCollectionBuilder( ) {  }
    
    
  public SuggestCollectionBuilder( String sourceHost, String suggestHost,
                                   List<QueryCollector> queryCollectors, Map<String,String> facetFieldMap,
                                   String contentCollection, String querySourceField, String sourceHandler,
                                   String suggesterCollection, String querySuggestField ) {
    this.sourceHost = sourceHost;
    this.suggestHost = suggestHost;
    this.sourceHandler = sourceHandler;
    if (sourceHandler == null) {
      sourceHandler = "/select";
    }
      
    this.queryCollectors = queryCollectors;
    this.facetFieldMap = facetFieldMap;
    this.contentCollection = contentCollection;
    this.querySourceField = querySourceField;

    this.suggesterCollection = suggesterCollection;
    this.querySuggestField = querySuggestField;
  }
    
  public SuggestCollectionBuilder( List<String> sourceZkHosts, List<String> suggestZkHosts,
                                   List<QueryCollector> queryCollectors, Map<String,String> facetFieldMap,
                                   String contentCollection, String querySourceField, String sourceHandler,
                                   String suggesterCollection, String querySuggestField ) {
    this.sourceZkHosts = sourceZkHosts;
    this.suggestZkHosts = suggestZkHosts;
    this.sourceHandler = sourceHandler;
    if (sourceHandler == null) {
      sourceHandler = "/select";
    }
        
    this.queryCollectors = queryCollectors;
    this.facetFieldMap = facetFieldMap;
    this.contentCollection = contentCollection;
    this.querySourceField = querySourceField;

    this.suggesterCollection = suggesterCollection;
    this.querySuggestField = querySuggestField;
  }
    
  public String getName( ) {
    return this.name;
  }
    
  public void setAddCounts( boolean addCounts ) {
    this.addCounts = addCounts;
  }
    
  public void initialize( Map<String,Object> config ) {
    LOG.debug( "initialize " + config );
    if (config == null) {
      LOG.error( "Cannot initialize - config is NULL!" );
      return;
    }
      
    this.name = String.valueOf( config.get( NAME ) );
      
      
    Object zkHostsOb = config.get( SOURCE_ZK_HOSTS );
    if (zkHostsOb != null) {
      if (zkHostsOb instanceof List ) {
        this.sourceZkHosts = (List<String>)zkHostsOb;
      }
      else if (zkHostsOb instanceof String) {
        String[] zkhosts = ((String)zkHostsOb).split( "," );
        this.sourceZkHosts = new ArrayList<String>( );
        for (int i = 0; i < zkhosts.length; i++) {
          sourceZkHosts.add( zkhosts[i] );
        }
      }
    }
      
    String sourceHost = String.valueOf( config.get( SOURCE_HOST ));
    if (sourceHost != null) this.sourceHost = sourceHost;
    LOG.debug( "sourceHost = " + sourceHost );
      
    String querySourceField = String.valueOf( config.get( QUERY_SOURCE_FIELD ));
    if (querySourceField != null) this.querySourceField = querySourceField;
    LOG.debug( "querySourceField = " + querySourceField );
      
    String contentCollection = String.valueOf( config.get( CONTENT_COLLECTION ));
    if (contentCollection != null) this.contentCollection = contentCollection;
    LOG.debug( "contentCollection = " + contentCollection );
      
    String suggestHost = String.valueOf( config.get( SUGGEST_HOST ));
    if (suggestHost != null) this.suggestHost = suggestHost;
    LOG.debug( "suggestHost = " + suggestHost );
      
    String querySuggestField = String.valueOf( config.get( QUERY_SUGGEST_FIELD ));
    if (querySuggestField != null) this.querySuggestField = querySuggestField;
    LOG.debug( "querySuggestField = " + querySuggestField );
      
    String suggesterCollection = String.valueOf( config.get( SUGGESTER_COLLECTION ));
    if (suggesterCollection != null) this.suggesterCollection = suggesterCollection;
    LOG.debug( "suggesterCollection = " + suggesterCollection );
      
    String sourceCollectionField = String.valueOf( config.get( SOURCE_COLLECTION_FIELD ));
    if (sourceCollectionField != null) this.sourceCollectionField = sourceCollectionField;
    LOG.debug( "sourceCollectionField = " + sourceCollectionField );
      
    Object qCollOb = config.get( QUERY_COLLECTORS );
    if (qCollOb != null && qCollOb instanceof List ) {
      LOG.debug( "adding query collectors ..." );
      this.queryCollectors = (List<QueryCollector>)qCollOb;
    }
      
    String sourceHandler = String.valueOf( config.get( SOURCE_HANDLER_FIELD ));
    if (sourceHandler != null) {
      this.sourceHandler = sourceHandler;
    }
      
    zkHostsOb = config.get( SUGGEST_ZK_HOSTS );
    if (zkHostsOb != null) {
      if (zkHostsOb instanceof List ) {
        this.suggestZkHosts = (List<String>)zkHostsOb;
      }
      else if (zkHostsOb instanceof String) {
        String[] zkhosts = ((String)zkHostsOb).split( "," );
        this.suggestZkHosts = new ArrayList<String>( );
        for (int i = 0; i < zkhosts.length; i++) {
          suggestZkHosts.add( zkhosts[i] );
        }
      }
    }
    
    Object facetFieldMapOb = config.get( "facetFieldMap" );
    if (facetFieldMapOb != null && facetFieldMapOb instanceof List) {
      List<Map<String,Object>> facetLst = (List<Map<String,Object>>)facetFieldMapOb;
      this.facetFieldMap = new HashMap<String,String>( );
      for (Map<String,Object> field : facetLst ) {
        for (String key: field.keySet( ) ) {
          Object val = field.get( key );
          System.out.println( "adding facet Field Mapping of " + key + " = " + val );
          facetFieldMap.put( key, String.valueOf( val ) );
        }
      }
    }
      
    Object facetContextFieldOb = config.get( "facetContextFields" );
    if (facetContextFieldOb != null && facetContextField instanceof Map) {
      this.facetContextField = new FacetContextField( (Map<String,Object>)facetContextFieldOb );
    }
  }
    
  public void buildCollection( ) {
    if (sourceHandler == null) {
      System.out.println( "Source Handler is NULL!!!" );
      sourceHandler = "/select";
    }
      
    if (deduplicate) {
      suggestions = new HashMap<String, SolrInputDocument>( );
      deduplicatedDocs = new ArrayList<SolrInputDocument>( );
    }
      
    // run each QueryCollector in its own thread
    // TO DO: Use Concurrent library
    LOG.debug( "buildCollection ");
    for (QueryCollector queryCollector : queryCollectors ) {
      LOG.debug( "starting queryCollector: " + queryCollector );
      // queryCollector.initialize( );
      queryCollector.setSuggestionProcessor( this );
      ++nCollectors;
      Thread collThread = new Thread( queryCollector );
      collThread.start( );
    }
      
    while (nCollectors > 0) {
      try {
        Thread.sleep( 500L );
      }
      catch( Exception e ) { }
    }
      
    LOG.debug( "Finishing up ..." );
    try {
      if (queryDocs.size() > 0 ) {
        synchronized( queryDocs ) {
          updateDocs( queryDocs, true );
          queryDocs.clear( );
        }
      }
        
      if (deduplicatedDocs != null && deduplicatedDocs.size( ) > 0) {
        System.out.println( "Updating deduplicated Docs" );
        synchronized( deduplicatedDocs ) {
          updateDocs( deduplicatedDocs, true );
        }
      }
    }
    catch ( Exception e ) {
          
    }
  }
    
  @Override
  public void addSuggestion( QuerySuggestion querySuggestion ) {
    LOG.debug( "addSuggestion '" + querySuggestion.getQuery( ) + "'" );
      
    // -----------------------------------------------------------------------------
    // execute the query with facet fields against the sourceCollection set
    // for each FacetField - get all the counts and
    // create a multi-value field with the count values
    // add the document to the Suggester index
    // -----------------------------------------------------------------------------
    try {
      List<FacetField> results = executeQuery( querySuggestion.getQuery( ) );
      
      SolrInputDocument solrDoc = new SolrInputDocument( );
        
      if ( deduplicate ) {
        if ( suggestions.keySet( ).contains( querySuggestion.getQuery( ) ) ) {
          System.out.println( "'" + querySuggestion.getQuery( ) + "' Is a Duplicate!" );
          addSuggestionData( solrDoc, querySuggestion, results, false );
          deduplicatedDocs.add( solrDoc );
          return;
        }
        suggestions.put( querySuggestion.getQuery( ), solrDoc );
      }
        
      if ( this.sourceCollectionField != null ) {
        solrDoc.addField( this.sourceCollectionField, this.contentCollection );
      }
    
      addSuggestionData( solrDoc, querySuggestion, results, true );
      queryDocs.add( solrDoc );
        
      if (queryDocs.size() >= batchSize ) {
        synchronized( queryDocs ) {
          updateDocs( queryDocs, false );
          queryDocs.clear( );
        }
      }
    }
    catch( SolrServerException sse ) {
            
    }
    catch ( IOException ioe ) {
      LOG.error( "Got IOException " + ioe );
    }
  }
    
  @Override
  public synchronized void collectorDone( QueryCollector qCollector ) {
    --nCollectors;
  }
    
    
  private List<FacetField> executeQuery( String queryString ) throws SolrServerException, IOException {
    String query = queryString.replace( ":", "" );
    query = query.replace( "/", "" );
      
    SolrQuery params = new SolrQuery( );
    if (querySourceField != null && querySourceField.equals( "null" ) == false ) {
      String qStr = querySourceField + ":\"" + query + "\"";
      System.out.println( "fielded content query " + qStr );
      params.set( CommonParams.Q, qStr );
    }
    else {
      System.out.println( "freetext content query '" + query + "'" );
      params.set( CommonParams.Q, query );
    }
    params.set( "facet.mincount", "1" );
    LOG.debug( "setting request handler to " + sourceHandler );
    params.setRequestHandler( sourceHandler );

    if (facetFieldMap != null || facetContextField != null) {
      params.set( "facet", "true" );
      if (facetFieldMap != null) {
        for (String facetField : facetFieldMap.keySet( ) ) {
          LOG.debug( "adding Facet Field: " + facetField );
          params.add( "facet.field", facetField );
        }
      }
      if (facetContextField != null) {
        for ( String facetField : facetContextField.facetFields ) {
          params.add( "facet.field", facetField );
        }
      }
    }
      
    // execute against sourceHost
    // return list of result FacetFields
    SolrClient sourceClient = getSourceClient( );
    QueryResponse resp = sourceClient.query( contentCollection, params );
    return resp.getFacetFields( );
  }
    
  private void updateDocs( List<SolrInputDocument> docs, boolean commit ) throws SolrServerException, IOException {
    LOG.debug( "updateDocs " + docs.size( ) );
    SolrClient suggestClient = getSuggestClient( );
    try {
      UpdateResponse resp = suggestClient.add( suggesterCollection, docs );
    }
    catch ( SolrServerException sse ) {
      LOG.error( "Got SolrServerExeption " + sse );
      throw sse;
    }
      
    if (commit) {
      LOG.debug( "committing to " + suggestHost );
      suggestClient.commit( suggesterCollection );
    }
    // check resp - if not OK, throw an Exception??
  }
    
  private void addSuggestionData( SolrInputDocument solrDoc, QuerySuggestion querySuggestion, List<FacetField> fFields, boolean isAdd ) {
    LOG.debug( "adding suggestion: '" + querySuggestField + " = " + querySuggestion.getQuery( ) + "' " + isAdd );
      
    if (isAdd) {
      solrDoc.addField( querySuggestField, querySuggestion.getQuery( ) );
      solrDoc.addField( "id", Integer.toString( nSuggestions++ ));  // maybe ID should be the suggestion so collection will dedupe?
    }
      
    if ( metaFieldMap != null ) {
      Map<String,Object> metadata = querySuggestion.getMetadata( );
      if (metadata != null ) {
        // translate metadata object to dynamic fields - or have a mapping??
        for (String metaField : metadata.keySet( ) ) {
          String suggestField  = metaFieldMap.get( metaField );
          Object metaVal = metadata.get( metaField );
          solrDoc.addField( suggestField, String.valueOf( metaVal ) );
        }
      }
    }
      
    List<FieldValue> fieldValues = querySuggestion.getFieldValues( );
    if (fieldValues != null) {
      for (FieldValue fieldVal : fieldValues ) {
        String value = fieldVal.toString( );
        if (isAdd || solrDoc.getFieldValues( fieldValuesField ) == null ) {
          solrDoc.addField( fieldValuesField, value );
        }
        else {
          Collection<Object> docValues = solrDoc.getFieldValues( fieldValuesField );
          boolean hasValue = false;
          for (Object docVal : docValues ) {
            if (docVal.toString( ).equals( value )) {
              hasValue = true;
              break;
            }
          }
          if (!hasValue) {
            solrDoc.addField( fieldValuesField, value );
          }
        }
      }
    }
    
    LOG.debug( "Got facet fields: " + fFields );
    if ( fFields != null ) {
        
      if (facetFieldMap != null) {
        for ( FacetField fField : fFields ) {
          LOG.debug( "Got facet field " + fField.getName( ) );
          String suggestField = facetFieldMap.get( fField.getName( ) );
          LOG.debug( "Got suggest field = " + suggestField );
          List<Count> values = fField.getValues();
          for (Count value : values ) {
            LOG.debug( "adding result facet value: " + suggestField + "=" + value.getName( ) + " " + value.getCount( ));
            
            String valStr = value.getName( );

            if (addCounts ) {
              valStr = valStr + " (" + Long.toString( value.getCount( ) ) + " )";
            }
            
            if (isAdd || solrDoc.getFieldValues( suggestField ) == null ) {
              solrDoc.addField( suggestField, valStr );
            }
          }
        }
      }
        
      if (facetContextField != null ) {
        String contextVal = facetContextField.getFacetContext( fFields );
        solrDoc.addField( facetContextField.fieldName, contextVal );
      }
    }
  }
    
  private SolrClient getSourceClient( ) {
    if (sourceClient != null) return sourceClient;
    
    synchronized( this ) {
      if (this.sourceHost != null) {
        this.sourceClient = new HttpSolrClient( sourceHost );
      }
      else if ( this.sourceZkHosts != null ) {
          this.sourceClient = new CloudSolrClient( sourceZkHosts, null );
      }
    }
      
    return sourceClient;
  }
    
  private SolrClient getSuggestClient( ) {
    if (suggestClient != null) return suggestClient;
      
    synchronized( this ) {
      if (this.suggestHost != null) {
        this.suggestClient = new HttpSolrClient( suggestHost );
      }
      else if ( this.suggestZkHosts != null ) {
        this.suggestClient = new CloudSolrClient( suggestZkHosts, null );
      }
    }
      
    return suggestClient;
  }
    
  // --------------------------------------------------------------
  // Java Bean Methods
  // To Do - make these mappable by Jackson ??
  // --------------------------------------------------------------
  public void setSuggestHost( String suggestHost ) {
    this.suggestHost = suggestHost;
  }
    
  public void setSuggestZkHosts( List<String> suggestZkHosts ) {
    this.suggestZkHosts = suggestZkHosts;
  }
    
  public void setSourceHost( String sourceHost ) {
    this.sourceHost = sourceHost;
  }
    
  public void setSourceZkHosts( List<String> sourceZkHosts ) {
    this.sourceZkHosts = sourceZkHosts;
  }
    
  public void setContentCollection( String contentCollection ) {
    this.contentCollection = contentCollection;
  }
    
  public void setSuggesterCollection( String suggesterCollection ) {
    this.suggesterCollection = suggesterCollection;
  }
    
  public void setQuerySourceField( String querySourceField ) {
    this.querySourceField = querySourceField;
  }
    
  public void setQuerySuggestField( String querySuggestField ) {
    this.querySuggestField = querySuggestField;
  }
    
  public void setQueryCollectors( List<QueryCollector> queryCollectors ) {
    this.queryCollectors = queryCollectors;
  }
    
  public void setFacetFieldMap( Map<String,String> facetFieldMap ) {
    this.facetFieldMap = facetFieldMap;
  }
    
  private class FacetContextField {
    String fieldName;
    List<String> facetFields;
    int maxNumFacets = -1;
    int minFacetCount = -1;
    String outputMode = "JSON";  // BQ | JSON | XML
      
    // constructor from Map<String,Object>
    FacetContextField( Map<String,Object> config ) {
      this.fieldName = String.valueOf( config.get( "fieldName" ) );
      this.facetFields = (List<String>)config.get( "facetFields" );
        
      String outputMode = String.valueOf( config.get( "outputMode" ));
      if (outputMode != null) this.outputMode = outputMode;
    }
      
    // String method to calculate facet context field value from List<FacetField>
    String getFacetContext( List<FacetField> facetFields ) {
      int nFacetVals = 0;
      StringBuilder stb = new StringBuilder( );
      if (outputMode.equals( "JSON" )) {
        stb.append( "[" );
      }
        
      for (FacetField fField : facetFields ) {
        if (outputMode.equals( "JSON" )) {
          if (nFacetVals > 0) {
            stb.append( "," );
          }
          stb.append( "{" );
          stb.append( "\"" ).append( fField.getName( ) ).append( "\":[" );
          List<Count> counts = fField.getValues( );
          for (int i = 0; i < counts.size( ) && (maxNumFacets == -1 || i < maxNumFacets); i++ ) {
            if (i > 0) stb.append( "," );
            Count cnt = counts.get( i );
            String val = cnt.getName( );
            long count = cnt.getCount( );
            stb.append( "{ \"value\":\"" ).append( val ).append( "\",\"count\":" ).append( Long.toString( count ) ).append( "}" );
          }
          stb.append( "]}" );
        }
        else if ( outputMode.equals( "BQ" )) {
          if (nFacetVals > 0) {
            stb.append( " " );
          }
          List<Count> counts = fField.getValues( );
          for (int i = 0; i < counts.size( ) && (maxNumFacets == -1 || i < maxNumFacets); i++ ) {
            if (i > 0) stb.append( " " );
            Count cnt = counts.get( i );
            String val = cnt.getName( );
            long count = cnt.getCount( );
            stb.append( fField.getName( ) ).append( ":\"" ).append( val ).append( "\"^" ).append( Long.toString( count ) );
          }
        }
      }
        
      return stb.toString( );
    }
  }

}
