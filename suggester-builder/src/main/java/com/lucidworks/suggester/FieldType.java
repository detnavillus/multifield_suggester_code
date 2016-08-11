package com.lucidworks.suggester;

public class FieldType {

  String fieldName;
  boolean multiValue;
    
  public FieldType( String fieldName, boolean multiValue ) {
    this.fieldName = fieldName;
    this.multiValue = multiValue;
  }
    
  public String getFieldName( ) {
    return this.fieldName;
  }
    
  public boolean isMultiValue( ) {
    return this.multiValue;
  }
}