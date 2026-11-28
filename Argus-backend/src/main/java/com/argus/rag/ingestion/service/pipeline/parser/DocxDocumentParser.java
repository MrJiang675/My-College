package com.argus.rag.ingestion.service.pipeline.parser;
import java.io.*;
import java.util.zip.*;
import javax.xml.stream.*;
import com.argus.rag.common.exception.BusinessException;
/** Streaming OOXML body extraction, without loading the ZIP or document DOM into the heap. */
public class DocxDocumentParser implements DocumentParser {
 public boolean supports(String ext){return "docx".equalsIgnoreCase(ext);}
 public String parse(InputStream input){
  try(var lease=ParseBudget.acquire();var zip=new ZipInputStream(input)) {
   ZipEntry entry; int count=0;
   while((entry=zip.getNextEntry())!=null && ++count<2000) {
    if(!entry.getName().equals("word/document.xml")) continue;
    var factory=XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);
    factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);
    // Bound expanded XML bytes too, to stop compressed ZIP bombs before building strings.
    var bounded=new FilterInputStream(zip){long bytes; public int read(byte[] b,int o,int l)throws IOException{
     int n=super.read(b,o,l); if(n>0 && (bytes+=n)>16L*1024*1024)throw new IOException("expanded XML limit");return n;
    } public int read()throws IOException{int n=super.read();if(n>=0 && ++bytes>16L*1024*1024)throw new IOException("expanded XML limit");return n;}};
    var xml=factory.createXMLStreamReader(bounded);
    var text=new StringBuilder(); boolean inText=false;
    try {
     while(xml.hasNext() && text.length()<200000) {
      int event=xml.next();
      if(event==XMLStreamConstants.START_ELEMENT)inText=xml.getLocalName().equals("t");
      if(event==XMLStreamConstants.CHARACTERS && inText)text.append(xml.getText(),0,Math.min(xml.getTextLength(),200000-text.length()));
      if(event==XMLStreamConstants.END_ELEMENT){if(xml.getLocalName().equals("p"))text.append('\n');inText=false;}
     }
     return text.toString().trim()+(text.length()>=200000?"\n【安全截断：最多提取 200000 字】":"");
    }finally{xml.close();}
   }
   throw new BusinessException("DOCX 缺少正文或结构不合法。");
  }catch(BusinessException known){throw known;}
  catch(Exception invalid){throw new BusinessException("DOCX 文档解析失败，文件损坏或解压内容过大。");}
 }
}
