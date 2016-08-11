package com.lucidworks.suggester;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.TermsParams;
import org.apache.solr.common.params.ModifiableSolrParams;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.SolrServerException;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets terms from the Terms Request Handler. For each field,
 */

public class TermVectorQueryCollector implements QueryCollector {
  private transient static final Logger LOG = LoggerFactory.getLogger( TermVectorQueryCollector.class );
    
  private static String termsHandler = "/terms";
    
  private List<String> termFields;
    
  private String solrHost;
  private List<String> zkHosts;
    
  private String collection;
    
  private SuggestionProcessor suggestProcessor;
    
  private SolrClient solrClient;
    
  public TermVectorQueryCollector( ) { }
    
  public TermVectorQueryCollector( String solrHost, List<String> termFields, String collection ) {
    this.solrHost = solrHost;
    this.termFields = termFields;
    this.collection = collection;
  }

  public TermVectorQueryCollector( List<String> zkHosts, List<String> termFields, String collection ) {
    this.zkHosts = zkHosts;
    this.termFields = termFields;
    this.collection = collection;
  }
    
  @Override
  public void initialize( Map<String,Object> config ) {
    Object solrHostOb = config.get( "solrHost" );
    this.solrHost = (solrHostOb != null) ? String.valueOf( solrHostOb ) : null;
  }

  @Override
  public void setSuggestionProcessor( SuggestionProcessor suggestProcessor ) {
    this.suggestProcessor = suggestProcessor;
  }

  public void run( ) {

    try {
      SolrClient solrClient = getSolrClient( );
      if (solrClient == null) {
        LOG.error( "Cant't get SolrClient for " + solrHost );
        return;
      }
        
      if (suggestProcessor == null) {
        LOG.error( "Suggest Processor is NULL!" );
        return;
      }

      ModifiableSolrParams params = new ModifiableSolrParams( );
      
      params.set( TermsParams.TERMS_LIMIT, -1);
      params.set( TermsParams.TERMS_SORT, TermsParams.TERMS_SORT_INDEX);
      String[] fields = termFields.toArray( new String[ termFields.size( )] );
      params.set( TermsParams.TERMS_FIELD, fields );
      params.set( CommonParams.QT, termsHandler );
      params.set( TermsParams.TERMS, "true" );
      
      QueryResponse rsp = solrClient.query( collection, params );
      TermsResponse termsResponse = rsp.getTermsResponse( );
      
      for (String fieldName : termFields ) {

        List<TermsResponse.Term> termList = termsResponse.getTerms( fieldName );
        if (termList != null) {
          for (TermsResponse.Term tc : termList) {
            String term = tc.getTerm();
            HashMap<String,Object> meta = new HashMap<String,Object>( );
            meta.put( "collection", collection );
            meta.put( "collector", "TermVectorQueryCollector" );
            meta.put( "field", fieldName );
          
            QuerySuggestion qSuggestion = new QuerySuggestion( term, meta, null );
            suggestProcessor.addSuggestion( qSuggestion );
          }
        }
      }
    }
    catch ( Exception e ) {
              
    }
    finally {
      suggestProcessor.collectorDone( this );
    }
  }
      
  private SolrClient getSolrClient( ) throws SolrServerException {
    if (this.solrClient != null) return this.solrClient;
          
    synchronized( this ) {
      if (this.solrHost != null) {
        this.solrClient = new HttpSolrClient( solrHost );
      }
    }
          
    return this.solrClient;
  }


}