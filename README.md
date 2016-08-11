# Multi-Field Solr Suggester

# I: Download Solr 6.0.1

	From: http://lucene.apache.org/solr/downloads.html
  

# II: Build Music Ontology Collection
  Download this github repository

  unzip the ontology_files.zip file
  In the directory where you installed Solr ([path-to-solr-install]) run the following commands (where [path-to-ontology-git] is where you downloaded this github repository.
  
  cd [path-to-solr-install]
  bin/solr start
  bin/solr create -c MusicOntology
  bin/post -c MusicOntology [path-to-this-install]/ontology_files/*.xml
  
  Test the collection:
  In a browser go to http://localhost:8983/solr
  
  Select the MusicOntology collection
  Click on the Query tab
  Issue a *:* query
  You should get 12408 records
  
# III: Install Query AutoFilter

  1) Copy the query-autofiltering-component-1.0.2.jar file to the Solr Webapp at:
     <path-to-solr-install>/server/solr-webapp/webapp/WEB-INF/lib
     
  4) Edit the solrconfig.xml in
     [path-to-solr-install]/server/solr/MusicOntology/conf/solrconfig.xml
     
     Add the /autofilter request handler configuration elements: (I put these after the /select requestHandler)
     
  <pre>
  &lt;requestHandler name="/autofilter" class="org.apache.solr.handler.component.SearchHandler">
    &lt;lst name="defaults">
      &lt;str name="echoParams">explicit&lt;/str>
      &lt;str name="df">name&lt;/str>
    &lt;/lst>
    &lt;lst name="excludeFields">
      &lt;str>isDistrib&lt;/str>
    &lt;/lst>
    &lt;arr name="first-components">
      &lt;str>autofilter&lt;/str>
    &lt;/arr>
    &lt;arr name="last-components">
      &lt;str>elevator&lt;/str>
    &lt;/arr>
  &lt;/requestHandler>
  &lt;searchComponent name="autofilter" 
                  class="org.apache.solr.handler.component.QueryAutoFilteringComponent" >
    &lt;str name="synonyms">synonyms.txt&lt;/str>
    &lt;arr name="excludeFields">
      &lt;str>related_entity1_ss&lt;/str>
      &lt;str>related_entity2_ss&lt;/str>
    &lt;/arr>
    &lt;arr name="verbModifiers">
      &lt;str>band members,members,member,was in,is in,who's in,who's in the,is in the,was in the:memberOfGroup_ss,groupMembers_ss&lt;/str>
      &lt;str>written,wrote,composed:composer_ss&lt;/str>
      &lt;str>performed,played,sang,recorded:hasPerformer_ss&lt;/str>
      &lt;str>covered,covers:hasPerformer_ss|version_s:Cover|original_performer:_ENTITY_,Recording_Type:Song=>original_performer|composer_ss:_ENTITY_&lt;/str>
    &lt;/arr>
  &lt;/searchComponent>
  </pre>
  
  5) restart Solr
    
     bin/solr restart
     
  6) Test that the Query Autofilter is working
  
    In the Solr Admin Console (web browser), change the request handler from '/select' to '/autofilter'
    Change the query to "who's in The Who"
    
    Should get 4 records back: Roger Daltrey,Pete Townshend,John Entwistle and Keith Moon
  

# IV: Build Music Ontology Suggester Collection
1) Run Ant to build the suggester code:

  If you don't have Ant installed, download it and install it from https://ant.apache.org/bindownload.cgi
  
  cd [path-to-this-install]/suggester-builder
  ant dist

2) Create MusicOntologySuggester collection on Solr (replace [path-to-solr-install] and [path-to-this-install] with the respective paths on your machine)

  cd [path-to-solr-install]
  bin/solr start
  bin/solr create -c MusicOntologySuggester
  
3) Edit the managed-schema file at [path-to-solr-install]/server/solr/MusicOntologySuggester/conf/managed-schema
   Add the following fieldType, field and copyField elements:
   
  <pre>
  &lt;fieldType name="text_suggest_ngram"
             class="solr.TextField" positionIncrementGap="100">
    &lt;analyzer type="index">
      &lt;tokenizer class="solr.StandardTokenizerFactory"/>
      &lt;filter class="solr.LowerCaseFilterFactory"/>
      &lt;filter class="solr.ASCIIFoldingFilterFactory"/>
      &lt;filter class="solr.EnglishPossessiveFilterFactory"/>
      &lt;filter class="solr.EdgeNGramFilterFactory" maxGramSize="10" minGramSize="1"/>
    &lt;/analyzer>
    &lt;analyzer type="query">
      &lt;tokenizer class="solr.StandardTokenizerFactory"/>
      &lt;filter class="solr.LowerCaseFilterFactory"/>
      &lt;filter class="solr.ASCIIFoldingFilterFactory"/>
      &lt;filter class="solr.EnglishPossessiveFilterFactory"/>
    &lt;/analyzer>
  &lt;/fieldType>
  &lt;field name="suggest_txt" type="string" indexed="true" stored="true" />
  &lt;field name="suggest_ngram" type="text_suggest_ngram" indexed="true" stored="false" multiValued="false"/>
  &lt;copyField source="suggest_txt" dest="suggest_ngram"/>
  </pre>
  
4) Edit the solrconfig.xml at [path-to-solr-install]/server/solr/MusicOntology/conf/solrconfig.xml
   Add the following request handler element:
   
  <pre>
  &lt;requestHandler name="/suggest_ngram" class="org.apache.solr.handler.component.SearchHandler">
    &lt;lst name="defaults">
      &lt;str name="wt">json&lt;/str>
      &lt;str name="defType">edismax&lt;/str>
      &lt;str name="rows">25&lt;/str>
      &lt;str name="fl">suggest_txt&lt;/str>
      &lt;str name="qf">suggest_txt^10 suggest_ngram&lt;/str>
    &lt;/lst>
  &lt;/requestHandler>
  </pre>
  
5) restart Solr

  cd [path-to-solr-install]
  bin/solr restart

6) Run the suggester builder script:
  
  Build the MusicOntologySuggester collection:
  
  cd [path-to-this-install]
  chmod +x runSuggester.sh
  ./runSuggester.sh

  Test the MusicOntologySuggester collection:
  <blockquote>
  Go to the Solr Admin Console (web browser) at localhost:8983/solr
  
  Select the MusicOntologySuggester collection
  
  Search *:*
  
  You should get 17020 records
  
  Search for "Jazz Drummers"
  
  You should get 2 records:
  
  <pre>
  "Afro-Cuban Jazz Drummers"
  "Jazz Drummers"
  </pre>
  </blockquote>
  
# V: Run the Typeahead Angular JS app

  1) Download and Install <a href="https://nodejs.org/en/download/">NodeJS</a> - there may be some dependencies that you will need to install.

  
  2) Use NodeJS to test the app
  
  Add this script to the nodejs directory (replace [path-to-this-install] with the path on you machine). Call it solr-typeahead.js or something
  
  var connect = require('connect'),
    serveStatic = require('serve-static');

  var app = connect();
  app.use(serveStatic("[path-to-this-install]/solr-angular-typeahead"));
  app.listen(5000);
  
  run the script
  node solr-typeahead.js
  
  Test the App with Dynamic Boosting:
  
  Open a Browser window to localhost:5000/index.html
  
  You should see the Typeahead Input bar
  
  Type 'J"
  
  Should see a dropdown starting with Jai Johnny Johanson Bands
  
  Now Type in 'Paul McCartney' and select 'Paul McCartney Songs'
  
  Now Type in 'J' again
  
  The top result should be 'John Lennon' now.
  
  
  Enjoy :-)
  
  

