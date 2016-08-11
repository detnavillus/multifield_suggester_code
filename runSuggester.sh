export configFile=SuggesterConfig.json
export jars=./suggester-builder/build/ivy/lib/compile
cp=.:$jars/solr-solrj-6.0.1.jar:$jars/solr-core-6.0.1.jar:./suggester-builder/dist/solr-suggester-builder-1.0.jar:$jars/jackson-core-asl-1.9.13.jar:$jars/jackson-mapper-asl-1.9.13.jar:$jars/slf4j-api-1.7.7.jar:$jars/httpclient-4.4.1.jar:$jars/httpcore-4.4.1.jar:$jars/httpmime-4.4.1.jar:$jars/commons-logging-1.1.1.jar:$jars/noggit-0.6.jar
export cp

java -Xmx4g -cp $cp com.lucidworks.suggester.SuggesterBuilder $configFile


