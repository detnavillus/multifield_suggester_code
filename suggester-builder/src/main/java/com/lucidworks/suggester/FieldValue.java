package com.lucidworks.suggester;


public class FieldValue {
  private String field;
  private String value;
    
  public FieldValue( String field, String value ) {
    this.field = field;
    this.value = value;
  }
    
  public String getField( ) {
    return this.field;
  }
    
  public String getValue( ) {
    return this.value;
  }
    
  public FieldValue copy( ) {
    return new FieldValue( this.field, this.value );
  }
    
  public String toString( ) {
    return field + ":" + value;
  }
}