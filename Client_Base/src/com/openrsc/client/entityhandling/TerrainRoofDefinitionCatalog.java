package com.openrsc.client.entityhandling;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Strict reader for the byte-identical shared effective roof inventory. */
public final class TerrainRoofDefinitionCatalog {
 public static final String RESOURCE = "/map-runtime/roof-definitions-v1.properties";
 private final int[] heights, fills;
 private TerrainRoofDefinitionCatalog(int[] heights,int[] fills){this.heights=heights;this.fills=fills;}
 private static final class Shared {
  private static final TerrainRoofDefinitionCatalog VALUE=read(TerrainRoofDefinitionCatalog.class.getResourceAsStream(RESOURCE));
 }
 public static TerrainRoofDefinitionCatalog shared(){return Shared.VALUE;}
 public static TerrainRoofDefinitionCatalog read(InputStream input){
  if(input==null)throw new IllegalStateException("Missing shared roof definition resource: "+RESOURCE);
  try(InputStream source=input){
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;
   while((n=source.read(buffer))!=-1){if(bytes.size()+n>32768)throw invalid("resource exceeds 32768-byte budget");bytes.write(buffer,0,n);}
   Properties properties=new Properties(){
    @Override public synchronized Object put(Object key,Object value){
     if(containsKey(key))throw invalid("duplicate key "+key);return super.put(key,value);
    }
   };
   properties.load(new ByteArrayInputStream(bytes.toByteArray()));
   if(!"1".equals(properties.getProperty("schemaVersion")))throw invalid("unsupported schemaVersion");
   String ids=properties.getProperty("ids");
   if(ids==null||ids.isEmpty())throw invalid("missing ordered IDs");
   String[] ordered=ids.split(",",-1);
   if(ordered.length>255)throw invalid("roof IDs exceed one-based byte carrier");
   int[] heights=new int[ordered.length],fills=new int[ordered.length];
   if(properties.size()!=ordered.length+2)throw invalid("unknown or incomplete definition fields");
   for(int id=0;id<ordered.length;id++){
    if(!String.valueOf(id).equals(ordered[id].trim()))throw invalid("IDs must be contiguous and declared in positional order: "+id);
    String row=properties.getProperty("roof."+id);if(row==null)throw invalid("missing roof."+id);
    String[] values=row.split(",",-1);if(values.length!=2)throw invalid("roof."+id+" requires height,fill");
    heights[id]=Integer.parseInt(values[0].trim());fills[id]=Integer.parseInt(values[1].trim());
    if(heights[id]<0||heights[id]>65535)throw invalid("roof."+id+" height must be 0..65535");
   }
   return new TerrainRoofDefinitionCatalog(heights,fills);
  }catch(IOException|IllegalArgumentException failure){throw new IllegalStateException("Malformed shared roof definition resource: "+failure.getMessage(),failure);}
 }
 private static IllegalStateException invalid(String reason){return new IllegalStateException("Malformed shared roof definition resource: "+reason);}
 public int getCount(){return heights.length;}
 public boolean hasDefinition(int id){return id>=0&&id<heights.length;}
 public int getHeight(int id){if(!hasDefinition(id))throw new IllegalArgumentException("Unknown roof definition: "+id);return heights[id];}
 public int getFill(int id){if(!hasDefinition(id))throw new IllegalArgumentException("Unknown roof definition: "+id);return fills[id];}
}
