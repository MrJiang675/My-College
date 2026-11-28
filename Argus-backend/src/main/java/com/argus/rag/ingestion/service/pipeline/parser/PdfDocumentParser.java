package com.argus.rag.ingestion.service.pipeline.parser;
import com.argus.rag.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.nio.file.*;
/** Disk-backed PDF parsing; bounded pages and output prevent unbounded vector queues. */
public class PdfDocumentParser implements DocumentParser {
 public boolean supports(String ext){return "pdf".equalsIgnoreCase(ext);}
 public String parse(InputStream input){
  Path temp=null;
  try(var lease=ParseBudget.acquire()) {
   temp=Files.createTempFile("argus-pdf-", ".pdf");
   Files.copy(input,temp,StandardCopyOption.REPLACE_EXISTING);
   try(var doc=PDDocument.load(temp.toFile(),MemoryUsageSetting.setupTempFileOnly())) {
    var stripper=new PDFTextStripper();
    var out=new BoundedTextWriter(200000);
    stripper.setEndPage(Math.min(150,doc.getNumberOfPages()));
    try{stripper.writeText(doc,out);}catch(BoundedTextWriter.LimitReached stop) { /* bounded output */ }
    return out.toString().trim()+(out.full()||doc.getNumberOfPages()>150?"\n【安全截断：最多解析 150 页 / 200000 字，请拆分原文】":"");
   }
  }catch(BusinessException known){throw known;}
  catch(Exception invalid){throw new BusinessException("PDF 文档解析失败，请检查是否加密或损坏。");}
  finally {if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
 }
}
