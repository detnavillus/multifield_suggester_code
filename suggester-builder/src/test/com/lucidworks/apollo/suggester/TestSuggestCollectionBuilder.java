package com.lucidworks.apollo.suggester;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.ModifiableSolrParams;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.SolrServerException;

import org.apache.solr.common.SolrDocument;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class TestSuggestCollectionBuilder extends TestCase {

  public void testSuggestCollectionBuilder( ) throws Exception {
    System.out.println( "testSuggestCollectionBuilder( )" );
    // create a content collection with facets (ACLs)
    MockSolrServer contentServer = new MockSolrServer( "TestSuggesterBuilder/content_coll/SolrServer.properties" );
    contentServer.startJetty( );
    contentServer.clearCollection( );
    contentServer.addDocuments( readFile( "TestSuggesterBuilder/AddContentDocs.xml" ) );
      
    // Use a FileQueryCollector
    ArrayList<QueryCollector> qCollectors = new ArrayList<QueryCollector>( );
    qCollectors.add( new FileQueryCollector( "TestSuggesterBuilder/testQueries.txt" ) );
    
    MockSolrServer suggesterServer = new MockSolrServer( "TestSuggesterBuilder/suggester_coll/SolrServer.properties" );
    suggesterServer.startJetty( );
    suggesterServer.clearCollection( );
      
    HashMap<String,String> facetFieldMap = new HashMap<String,String>( );
    facetFieldMap.put( "acl_ss", "acl_ss" );
      
    // build the suggester collection in a JettySolrRunner
    SuggestCollectionBuilder suggestBuilder = new SuggestCollectionBuilder( "http://localhost:8983/solr", "http://localhost:8984/solr/",
                                                                           qCollectors, facetFieldMap, "collection1", "text_txt", "/select", "collection1", "suggest_txt" );
    suggestBuilder.setAddCounts( false );
    suggestBuilder.buildCollection( );
      
    checkCollectionValidity(  );
      
    contentServer.stopJetty( );
    suggesterServer.stopJetty( );
  }
    
  public void testSuggesterCollectionBuilderFactory( ) throws Exception {
    System.out.println( "testSuggesterCollectionBuilderFactory( )" );
    MockSolrServer contentServer = new MockSolrServer( "TestSuggesterBuilder/content_coll/SolrServer.properties" );
    contentServer.startJetty( );
    contentServer.clearCollection( );
    contentServer.addDocuments( readFile( "TestSuggesterBuilder/AddContentDocs.xml" ) );
        
    MockSolrServer suggesterServer = new MockSolrServer( "TestSuggesterBuilder/suggester_coll/SolrServer.properties" );
    suggesterServer.startJetty( );
    suggesterServer.clearCollection( );
        
    SuggestCollectionBuilder suggestBuilder = SuggestCollectionBuilderFactory.createSuggestCollectionBuilder( collectorConfig );
      
    suggestBuilder.buildCollection( );
      
    checkCollectionValidity(  );
      
    contentServer.stopJetty( );
    suggesterServer.stopJetty( );
  }

    
  private void checkCollectionValidity( ) {
    // --------------------------------------------------------------
    // wait for suggestBuilder to be done
    //
    // test that queries against the suggester collection should
    // not return if the ACL filter is incorrect for the suggester
    // query suggester collection fq=acl_ss:normal  - should get back 'bim'
    // query suggester collection fq=acl_ss:secret  - should get back 'bim', 'bamm'
    // query suggester collection fq=acl_ss:"top secret" - should get back 'bim', 'bamm', 'boom'
    // --------------------------------------------------------------
    try {
      SolrClient solrClient = getSuggesterCollClient( );
      ModifiableSolrParams params = new ModifiableSolrParams( );
      params.set( CommonParams.Q, "*:*" );
      params.set( CommonParams.FL, "suggest_txt" );
      params.set( CommonParams.FQ, "acl_ss:normal" );
      params.set( "rows", "10" );
        
      QueryResponse rsp = solrClient.query( "collection1", params );
      boolean responseValid = responseIs( rsp, "suggest_txt", "bim" );
      System.out.println( "responseValid = " + responseValid );
      //assertTrue( responseValid );
        
      params = new ModifiableSolrParams( );
      params.set( CommonParams.Q, "*:*" );
      params.set( CommonParams.FL, "suggest_txt" );
      params.set( CommonParams.FQ, "acl_ss:secret" );
      rsp = solrClient.query( "collection1", params );
      responseValid = responseIs( rsp, "suggest_txt", "bim", "bamm" );
      System.out.println( "responseValid = " + responseValid );
      //assertTrue( responseValid );
        
      params = new ModifiableSolrParams( );
      params.set( CommonParams.Q, "*:*" );
      params.set( CommonParams.FL, "suggest_txt" );
      params.set( CommonParams.FQ, "acl_ss:\"top secret\"" );
      rsp = solrClient.query( "collection1", params );
      responseValid = responseIs( rsp, "suggest_txt", "bim", "bamm", "boom" );
      System.out.println( "responseValid = " + responseValid );
      //assertTrue( responseValid );
    }
    catch ( Exception e )
    {
      System.out.println( "Got EXCEPTION!: " + e );
      assertTrue( false );
    }
  }
    
  private synchronized boolean responseIs( QueryResponse rsp, String field, String ... queries ) {
    System.out.println( field  + " " + rsp.getResults( ).size( ) );
    HashSet<String> expected = new HashSet<String>( );
    for ( int i = 0; i < queries.length; i++) {
      System.out.println( "adding '" + queries[i] + "'" );
      expected.add( queries[i] );
    }
    for (SolrDocument doc : rsp.getResults() ) {
      String val = String.valueOf( doc.getFirstValue( field ) );
      System.out.println( "   '" + val + "'" );
      if (expected.contains( val )) {
        System.out.println( "removing '" + val + "'" );
        expected.remove( val );
      }
    }
    System.out.println( expected.size( ) + " left " );
    return expected.size() == 0;
  }
    
  private SolrClient getSuggesterCollClient( ) throws SolrServerException {
    return new HttpSolrClient( "http://localhost:8984/solr" );
  }
    
  private String readFile( String dataFile ) {
    StringBuilder strb = new StringBuilder( );
    try {
      InputStream is = TestSuggestCollectionBuilder.class.getClassLoader( ).getResourceAsStream( dataFile );
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
    
  private static final String collectorConfig = "{ \"name\":\"Secrets\","
                                              + "  \"contentCollection\":\"collection1\","
                                              + "  \"sourceHost\":\"http://localhost:8983/solr\","
                                              + "  \"querySourceField\":\"text_txt\","
                                              + "  \"suggesterCollection\":\"collection1\","
                                              + "  \"sourceHandler\":\"/select\","
                                              + "  \"suggestHost\":\"http://localhost:8984/solr\","
                                              + "  \"querySuggestField\":\"suggest_txt\","
                                              + "  \"facetFieldMap\": [{\"acl_ss\":\"acl_ss\"}],"
                                              + "  \"queryCollectors\": ["
                                              + "   { \"type\":\"FileQueryCollector\","
                                              + "     \"fileName\":\"TestSuggesterBuilder/testQueries.txt\""
                                              + "   }"
                                              + " ]"
                                              + "}";
}
