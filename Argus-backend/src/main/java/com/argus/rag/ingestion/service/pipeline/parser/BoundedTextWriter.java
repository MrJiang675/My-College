package com.argus.rag.ingestion.service.pipeline.parser;
import java.io.Writer;
final class BoundedTextWriter extends Writer {
 static final class LimitReached extends java.io.IOException {}
 private final StringBuilder text=new StringBuilder();
 private final int limit;
 BoundedTextWriter(int limit){this.limit=limit;}
 public void write(char[] chars,int off,int len)throws java.io.IOException{
  int allowed=Math.min(len,limit-text.length());text.append(chars,off,allowed);
  if(allowed<len)throw new LimitReached();
 }
 boolean full(){return text.length()>=limit;}
 public void flush(){} public void close(){} public String toString(){return text.toString();}
}
