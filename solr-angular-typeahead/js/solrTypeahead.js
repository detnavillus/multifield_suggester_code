var typeAhead = angular.module('solrTypeahead', []);

typeAhead.controller('TypeAheadController', function( $scope, $http ){
  $scope.$watch('query', function() {

    var fields = 'suggest_txt,' + $scope.boostFields.toString();
    console.log( 'using fields = ' + fields );
    
    // set up boost query from recent selected items list
    var bq = $scope.get_boost( );
    
    $http({
      method: 'JSONP',
      url: $scope.typeaheadUrl,
      params:{ 'json.wrf': 'JSON_CALLBACK',
               'q': "\"" + $scope.query + "\"",
               'defType': 'edismax',
               'fl': fields,
               'bq': bq
             }
    }).success(function(data) {
        $scope.suggestions = data.response.docs;
        console.log('search success!');
        
    }).error(function() {
        console.log('Search failed!');
    });
  });

  $scope.prompt = 'Enter your search term';
  $scope.typeaheadUrl = 'http://localhost:8983/solr/MusicOntologySuggester/suggest_ngram';
  $scope.queryUrl = 'http://localhost:8983/solr/MusicOntology/autofilter';
  $scope.boostFields = ['genres_ss','hasPerformer_ss','composer_ss','memberOfGroup_ss'];
  $scope.query = '';
  
  $scope.boostArray = ['null','null','null','null'];
  $scope.current_loc_counter = $scope.boostArray.length - 1;
  
  $scope.onItemSelected=function( selectedItem ){
    console.log( selectedItem );
    $scope.query = selectedItem;
    
    $scope.add_selected_item(  );
    
    $scope.current=0;
    $scope.selected=true;
	
    // fire the query to get the docs from MusicOntology
    // output = $scope.docs
    $http({
      method: 'JSONP',
      url: $scope.queryUrl,
      params:{ 'json.wrf': 'JSON_CALLBACK',
               'wt': 'json',
               'q': $scope.query,
               'fl': 'name'
             }
    }).success(function(data) {
        $scope.docs = data.response.docs;
        console.log('search success!');
        
    }).error(function() {
        console.log('Search failed!');
    });
  };
  
  $scope.add_selected_item=function(  ) {
    // add the selectedItem to recent selected items list
    $scope.boostArray[$scope.current_loc_counter] = $scope.suggestions[$scope.current];
      
    console.log( 'add_selected_item: selected index = ' + $scope.current );
    console.log( 'selected genres_ss = ' + $scope.boostArray[$scope.current_loc_counter].genres_ss );
    console.log( 'selected hasPerformer_ss = ' + $scope.boostArray[$scope.current_loc_counter].hasPerformer_ss );
    console.log( 'selected composer_ss = ' + $scope.boostArray[$scope.current_loc_counter].composer_ss );
    console.log( 'selected memberOfGroup_ss = ' + $scope.boostArray[$scope.current_loc_counter].memberOfGroup_ss );  
      
    --$scope.current_loc_counter;
    if ($scope.current_loc_counter < 0)
      $scope.current_loc_counter = $scope.boostArray.length - 1;
  };
  
  $scope.get_boost=function( ) {
    var boostQuery = '';
    var done = false;
    var counter = $scope.current_loc_counter;
    var boost_factor = 100.0;
    var multiplier = 0.5;
    while (!done) {
      var obj = $scope.boostArray[counter];
      if (obj != 'null') {
        for (var f = 0; f < $scope.boostFields.length; f++) {
          if ( obj[$scope.boostFields[f]] ) {
            var boosts = String( obj[$scope.boostFields[f]] ).split( "," );
            for (var i = 0; i < boosts.length; i++) {
              if (boostQuery.length > 0) boostQuery += ' ';
              boostQuery += $scope.boostFields[f] + ":\"" + boosts[i] + "\"^" + String( boost_factor );
              if (i == 3) break;
            }
          }
        }       
      }
      
      boost_factor = boost_factor * multiplier;
      ++counter;
      if (counter >= $scope.boostArray.length)
        counter = 0;
      if (counter == $scope.current_loc_counter)
        done = true;
    }
    
    console.log( "adding boost: " + boostQuery );
    return boostQuery;
  };
  
  $scope.isCurrent=function( index ){
    return $scope.current == index;
  };
	  
  $scope.setCurrent=function( index ){
    $scope.current=index;
  };
});


