package com.lucidworks.suggester;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.PivotField;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.SolrServerException;

import org.apache.solr.common.util.NamedList;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrDocument;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects phrases over a set of phrases defined by a list of field patterns
 * uses pivot faceting to get all permutations for a given field list.
 *
 * Field Patterns have the form: ${field_name}'s ${field_name} for ${field_name}
 *
 * Configuration parameters
 * Solr Host or ZK Hosts
 * List of Field Patterns
 */

public class PivotFacetQueryCollector implements QueryCollector {
  private transient static final Logger LOG = LoggerFactory.getLogger( PivotFacetQueryCollector.class );
    
  private String solrHost;
  private List<String> zkHosts;
    
  private ArrayList<String> fieldPatterns;
    
  private SolrClient solrClient;
  private String collection;
    
  private String query = "*:*";
    
  private static final Pattern pat = Pattern.compile("(\\$\\{[\\w\\.]+\\})");
    
  private boolean discardAbsent = true;
    
  private SuggestionProcessor suggestProcessor;
    
  private HashMap<String,String> patternFilterMap;
    
  public PivotFacetQueryCollector( ) {  }
    
  // how to get collection with ZK???
    
  public PivotFacetQueryCollector( String solrHost, List<String> fieldPatterns ) {
    this(solrHost, fieldPatterns, null );
  }
    
  public PivotFacetQueryCollector( String solrHost, List<String> fieldPatterns, String collection ) {
    this.solrHost = solrHost;
    initFieldPatterns( fieldPatterns );
    this.collection = collection;
  }
    
  public PivotFacetQueryCollector( List<String> zkHosts, List<String> fieldPatterns, String collection ) {
    this.zkHosts = zkHosts;
    initFieldPatterns( fieldPatterns );
    this.collection = collection;
  }
    
  public PivotFacetQueryCollector( SolrClient solrClient, List<String> fieldPatterns, String collection ) {
    this.solrClient = solrClient;
    initFieldPatterns( fieldPatterns );
    this.collection = collection;
  }
    
  @Override
  public void initialize( Map<String,Object> config ) {
    LOG.info( "PivotFacetQueryCollector.initialize( )" );
    // get "solrServer" if list of zkhost, create List<String>
    Object solrServer = config.get( "solrHost" );
    if (solrServer != null) {
      String ss = solrServer.toString( );
      LOG.debug( "Got Solr Server URL = " + ss );
      if (ss.startsWith( "http://") || ss.startsWith( "https://")) {
        this.solrHost = ss;
      }
      else {
        String[] zkhosts = ss.split( "," );
        this.zkHosts = new ArrayList<String>( );
        for (int i = 0; i < zkhosts.length; i++) {
          zkHosts.add( zkhosts[i] );
        }
      }
    }
      
    List<String> fieldPats = (List<String>)config.get( "fieldPatterns" );
    initFieldPatterns( fieldPats );
  }
    
  private void initFieldPatterns( List<String> fieldPats ) {
    LOG.info( "PivotFacetQueryCollector.initFieldPatterns( )" );
    if (fieldPats != null) {
      LOG.info( "got " + fieldPats.size( ) + " field patterns ");
      this.fieldPatterns = new ArrayList<String>( fieldPats.size( ) );
      // iterate field patterns - if fieldPattern has | in it, get the field pattern and the filter query
      for (String fieldPattern : fieldPats ) {
        LOG.info( "Adding field pattern: " + fieldPattern );
        if (fieldPattern.indexOf( "|" ) > 0) {
          String[] parts = fieldPattern.split( "\\|" );
          fieldPatterns.add( parts[0] );
          if (patternFilterMap == null) patternFilterMap = new HashMap<String,String>( );
          LOG.info( "Adding " + parts[0] + " filter: " + parts[1] );
          patternFilterMap.put( parts[0], parts[1] );
        }
        else {
          fieldPatterns.add( fieldPattern );
        }
      }
    }
  }
   
  @Override
  public void setSuggestionProcessor( SuggestionProcessor suggestProcessor ) {
    this.suggestProcessor = suggestProcessor;
  }
    

  public void run(  ) {
    LOG.info( "PivotFacetQueryCollector.run( )" );
      
    for (String fieldPattern : fieldPatterns ) {

      try {
        getQuerySuggestions( fieldPattern, suggestProcessor );
      }
      catch (Exception e ) {
        System.out.println( "Got Exception: " + e );
      }
    }
      
    suggestProcessor.collectorDone( this );
  }
    
  private void getQuerySuggestions( String fieldPattern, SuggestionProcessor suggestProcessor ) throws Exception {
    LOG.info( "PivotFacetQueryCollector.getQuerySuggestions " + fieldPattern );
    String pivotFields = getPivotFields( fieldPattern );
    LOG.info( "pivotFields = " + pivotFields );

      
    ModifiableSolrParams params = new ModifiableSolrParams( );
    HashMap<String,String> qParams = parseQuery( this.query );
    for (String param : qParams.keySet( ) ) {
      params.set( param, qParams.get( param ) );
    }
    params.set( "facet", "true" );
    params.set( "facet.pivot", pivotFields );
    params.set( "facet.mincount", "1" );
    params.set( "rows", "0" );  // just want the facets
    params.set( "facet.limit", "-1" );
      
    if (patternFilterMap != null && patternFilterMap.get( fieldPattern ) != null ) {
      LOG.info( "Adding filter query: " + patternFilterMap.get( fieldPattern ) );
      params.set( "fq", patternFilterMap.get( fieldPattern ));
    }
    
    try {
      SolrClient client = getSolrClient( );
      LOG.info( "got Solr Client " + client );
      LOG.info( "querying collection " + this.collection );
      QueryResponse resp = client.query( this.collection, params );
      getQuerySuggestions( resp, fieldPattern, suggestProcessor );
    }
    catch( SolrServerException sse ) {
      LOG.info( "Got SolorServerException: " + sse );
      throw new Exception( "Got SolrServerException: " + sse.getMessage( ) );
    }
    catch( IOException ioe ) {
      throw new Exception( "Got IOException: " + ioe.getMessage( ) );
    }
  }
    
  private HashMap<String,String> parseQuery( String query ) {
    HashMap<String,String> qParams = new HashMap<String,String>( );
    if (query.indexOf( "&" ) >= 0) {
      String[] paramLst = query.split( "&" );
      for (int i = 0; i < paramLst.length; i++ ) {
        String[] nameval = paramLst[i].split( "=" );
        qParams.put( nameval[0], nameval[1] );
      }
    }
    else {
      qParams.put( CommonParams.Q, query );
    }
    return qParams;
  }
    
  private void getQuerySuggestions( QueryResponse response, String fieldPattern, SuggestionProcessor suggestProcessor ) {
    LOG.info( "response: " + response.getResults().getNumFound() );

    NamedList<List<PivotField>> pivotFields = response.getFacetPivot();
    
    for ( Map.Entry<String,List<PivotField>> pivotLst : pivotFields ) {
      List<PivotField> pivots = pivotLst.getValue( );
      for (PivotField pivot : pivots ) {
        addPivots( suggestProcessor, fieldPattern, pivot, new ArrayList<FieldValue>( ) );
      }
    }
  }
    
    
  private void addPivots( SuggestionProcessor suggestProcessor, String fieldPattern, PivotField pf, List<FieldValue> fieldValues ) {
    LOG.info( "addPivots " +  pf.getField( ) + ":" + pf.getValue( ) + " ( " + fieldValues.size( ) + " )" );

    fieldValues.add( new FieldValue( pf.getField( ), String.valueOf( pf.getValue( ) ) ));
      
    List<PivotField> subPivots = pf.getPivot();
    if ( subPivots != null && subPivots.size() > 0) {
      LOG.info( "Got " + subPivots.size() + " sub pivots " );
      for (PivotField pvf : subPivots ) {
        ArrayList<FieldValue> subFields = new ArrayList<FieldValue>( );
        subFields.addAll( fieldValues );
        addPivots( suggestProcessor, fieldPattern, pvf, subFields );
      }
    }
    else {
      // Now translate the list of FieldValue into a String using the fieldPattern
      String queryString = translateQueryString( fieldPattern, fieldValues );
      if (queryString != null && queryString.indexOf( "${" ) < 0) {
        LOG.info( "Got query String " + queryString );
        HashMap<String,Object> meta = new HashMap<String,Object>( );
        meta.put( "count", new Integer( pf.getCount( ) ) );
        meta.put( "collector", "PivotFacetQueryCollector" );
        
        QuerySuggestion qSuggestion = new QuerySuggestion( queryString, meta, fieldValues );
        suggestProcessor.addSuggestion( qSuggestion );
      }
    }
  }

  private String getPivotFields( String fieldPattern ) {
    StringBuilder strb = new StringBuilder( );
      
    Matcher m = pat.matcher( fieldPattern );

    while (m.find()) {
      String fieldName = m.group();
      fieldName = fieldName.substring(2, fieldName.length() - 1);
        if (strb.length() > 0) strb.append( "," );
      strb.append( fieldName );
    }
      
    return strb.toString( );
  }
    
  private String translateQueryString( String fieldPattern, List<FieldValue> fieldValues ) {
    Matcher m = pat.matcher( fieldPattern );

    StringBuffer sb = null;
    int i = 0;
    while (m.find() && i < fieldValues.size( )) {
      if (sb == null) {
        sb = new StringBuffer();
      }

      String fieldName = m.group();
      fieldName = fieldName.substring(2, fieldName.length() - 1);
      FieldValue fv = fieldValues.get( i++ );
      String val = fv.getValue( );
      if (val == null || !fv.getField( ).equals( fieldName ) ) {
        if (discardAbsent) {
          return null;
        }
        val = "";
      }
        
      // may want to fix issues like 'nounss' here ...
      m.appendReplacement(sb, val);
    }
    if (sb != null) {
      m.appendTail(sb);
      return sb.toString();
    } else {
      return fieldPattern;
    }
  }
    
  // Need To Throw Something here ...
  private SolrClient getSolrClient( )throws SolrServerException {
    // depending on solrHost url - return HttpSolrClient or CloudSolrClient
    if (this.solrClient != null) return this.solrClient;
        
    synchronized( this ) {
      if (this.solrHost != null) {
        this.solrClient = new HttpSolrClient( solrHost );
      }
      else if ( this.zkHosts != null ) {
        this.solrClient = new CloudSolrClient( zkHosts, null );
        if (this.collection != null) {
          ((CloudSolrClient)solrClient).setDefaultCollection( this.collection );
        }
      }
    }
        
    return this.solrClient;
  }
  
}
