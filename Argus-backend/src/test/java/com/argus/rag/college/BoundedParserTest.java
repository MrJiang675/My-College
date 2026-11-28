package com.argus.rag.college;
import com.argus.rag.ingestion.service.pipeline.parser.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class BoundedParserTest {
 @Test void unicodeTextIsBoundedWithoutSplittingEncodedCharacters(){
  String text="大学实验".repeat(60000);
  String result=new TxtDocumentParser().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  assertTrue(result.contains("安全截断"));assertTrue(result.length()<200100);
  assertEquals("中文",new TxtDocumentParser().parse(new ByteArrayInputStream("中文".getBytes(Charset.forName("GB18030")))));
 }
 @Test void docxExtractsParagraphsAndTableTextInOrder()throws Exception {
  var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){
   zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write("<w:document xmlns:w='test'><w:body><w:p><w:r><w:t>课程</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>表格</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
  }
  assertEquals("课程\n表格",new DocxDocumentParser().parse(new ByteArrayInputStream(bytes.toByteArray())));
 }
 @Test void pdfPageLimitIsExplicit()throws Exception {
  var bytes=new ByteArrayOutputStream();try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()){for(int i=0;i<151;i++)pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(bytes);}
  assertTrue(new PdfDocumentParser().parse(new ByteArrayInputStream(bytes.toByteArray())).contains("150 页"));
 }
}
