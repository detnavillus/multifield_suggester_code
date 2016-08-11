package com.lucidworks.apollo.suggester;

import org.apache.solr.client.solrj.embedded.JettyConfig;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.SolrServerException;

import org.apache.solr.common.SolrInputDocument;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;

import java.net.URL;

import junit.framework.TestCase;

public class TestPivotFacetQueryCollector extends TestCase {
    
  public TestPivotFacetQueryCollector( ) {  }
    
  public void testPVCollector( ) {
      
    try {
      MockSuggestionProcessor sugProc = new MockSuggestionProcessor( );
      
      // Set up an EmbeddedSolrServer, get schema.xml etc from resources ...
      MockSolrServer ss = new MockSolrServer( "TestPivotFacetQueryCollector/SolrServer.properties" );
      ss.startJetty( );
      ss.clearCollection( );
      ss.addDocuments( readFile( "TestPivotFacetQueryCollector/AddSolrDocs.xml" ) );

      // add some field patterns ...
      // get these from TestPivotFacetQueryCollectorFieldPatterns ...
      List<String> fieldPatterns = getFieldPatterns( "TestPivotFacetQueryCollector/FieldPatterns.txt" );
      
      PivotFacetQueryCollector pivotColl = new PivotFacetQueryCollector( ss.getSolrClient( ), fieldPatterns, ss.getCollection( ) );
      pivotColl.setSuggestionProcessor( sugProc );
      pivotColl.run( );
      
      List<QuerySuggestion> suggestions = sugProc.getSuggestions( );
      System.out.println( "Got " + ((suggestions != null) ? suggestions.size( ) : 0) + " suggestions!" );
      for (QuerySuggestion suggestion: suggestions ) {
        System.out.println( suggestion.getQuery( ) );
      }
      
      ss.stopJetty( );
    }
    catch ( Exception e ) {
      System.out.println( "testPVCollector: got Exception: " + e );
      e.printStackTrace();
      assertTrue( false );  // fail the test!
    }
  }

    
  private List<String> getFieldPatterns( String fieldPatternsFile ) {
    ArrayList<String> fieldPatterns = new ArrayList<String>(  );
    try {
      InputStream is = TestPivotFacetQueryCollector.class.getClassLoader( ).getResourceAsStream( fieldPatternsFile );
      BufferedReader br = new BufferedReader( new InputStreamReader( is ) );
      String line = null;
      while ((line = br.readLine( ) ) != null) {
        fieldPatterns.add( line );
      }
      br.close( );
    }
    catch ( IOException ioe ) {
      System.out.println( "Got IOException: " + ioe );
    }

    return fieldPatterns;
  }
    
  private String readFile( String dataFile ) {
    StringBuilder strb = new StringBuilder( );
    try {
      InputStream is = TestPivotFacetQueryCollector.class.getClassLoader( ).getResourceAsStream( dataFile );
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
