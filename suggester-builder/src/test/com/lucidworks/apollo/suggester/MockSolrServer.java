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


public class MockSolrServer {
    
  public static final String SOLR_HOME = "solrHome";
  public static final String CONFIG_SET = "configset";
  public static final String COLLECTION = "collection";
  public static final String PORT = "port";
    
  JettySolrRunner jetty;
    
  HttpSolrServer client;
    
  String solrHome;
  String configset;
  String collection = "collection1";
  int port = 8983;
    
  boolean serverRunning = false;
    
  public MockSolrServer( String propertiesFile ) {
    Properties props = new Properties();
    try {
      InputStream is = MockSolrServer.class.getClassLoader().getResourceAsStream( propertiesFile );
      BufferedReader br = new BufferedReader( new InputStreamReader( is ) );
      String line = null;
      while ((line = br.readLine( ) ) != null) {
        String[] pair = line.split( ":" );
        props.setProperty( pair[0].trim(), pair[1].trim( ) );
      }
      br.close( );
    }
    catch ( IOException ioe ) {
            
    }
        
    init( props );
  }
    
  void init( Properties props ) {
    this.solrHome = props.getProperty( SOLR_HOME );
    this.configset = props.getProperty( CONFIG_SET );
    this.collection = props.getProperty( COLLECTION );
    this.port = Integer.parseInt( props.getProperty( PORT ) );
  }
    
  String getServerHostURL( ) {
    if (jetty == null) return null;
    return "http://localhost:" + Integer.toString( jetty.getLocalPort() ) + "/solr";
  }
    
  String getHomeDir( ) {
    return solrHome;
  }
    
  String getDataDir( ) {
    return solrHome + "/" + this.collection + "/data";
  }
    
  public String getCollection( ) {
    return this.collection;
  }
    
  void startJetty() throws Exception {
    Properties props = new Properties();
    props.setProperty("solrconfig", "solrconfig.xml");
    props.setProperty("solr.data.dir", getDataDir());
    
    JettyConfig jettyConfig = JettyConfig.builder(buildJettyConfig("/solr")).setPort(port).build();
    
    jetty = new JettySolrRunner( getHomeDir(), props, jettyConfig );
    jetty.start();
    int newPort = jetty.getLocalPort();
    if (port != 0 && newPort != port) {
      throw new Exception( "TESTING FAILURE: could not grab requested port." );
    }
        
    this.port = newPort;
  }
    
  void stopJetty( ) throws Exception {
    if (jetty != null && jetty.isRunning( )) jetty.stop();
  }
    
  void clearCollection( ) throws Exception {
    try {
      HttpSolrServer client = getSolrClient( );
      client.deleteByQuery( collection, "*:*" );
      client.commit( collection );
    }
    catch ( Exception e ) {
      System.out.println( "Got Exception! " + e );
    }
  }
    
  void addDocuments( String solrXMLData )  {
    try {
      List<SolrInputDocument> solrDocs = getSolrInputDocuments( solrXMLData );
      HttpSolrServer client = getSolrClient( );
      client.add( collection, solrDocs );
      client.commit( collection );
    }
    catch ( Exception e ) {
      System.out.println( "Got Exception! " + e );
    }
  }
    
  List<SolrInputDocument> getSolrInputDocuments( String solrXMLData ) throws Exception {
    ArrayList<SolrInputDocument> solrDocs = new ArrayList<SolrInputDocument>( );
        
    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = builderFactory.newDocumentBuilder();
    Document xmlDocument = builder.parse( new ByteArrayInputStream( solrXMLData.getBytes() ) );
        
    NodeList docEls = xmlDocument.getElementsByTagName( "doc" );

    for (int i = 0; i < docEls.getLength( ); i++) {
      Element docEl = (Element)docEls.item( i );
      SolrInputDocument solrDoc = new SolrInputDocument( );
      solrDocs.add( solrDoc );
            
      NodeList fieldEls = docEl.getElementsByTagName( "field" );

      for (int f = 0; f < fieldEls.getLength(); f++ ) {
        Element fieldEl = (Element)fieldEls.item( f );
        solrDoc.addField( fieldEl.getAttribute( "name" ), fieldEl.getTextContent() );
      }
    }
        
    return solrDocs;
  }
    
    
  HttpSolrServer getSolrClient( ) throws Exception, SolrServerException {
    if (client != null) return client;
    // make sure that the server is running ...
    if (jetty == null || !jetty.isRunning( ) ) {
      throw new Exception( "Jetty Solr is not running!" );
    }
        
    this.client = new HttpSolrServer( getServerHostURL( )  );
    return client;
  }
    
  JettyConfig buildJettyConfig( String context ) {
    return JettyConfig.builder().setContext( context ).withSSLConfig( null ).build();
  }
}