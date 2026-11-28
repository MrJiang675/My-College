package com.argus.rag.ingestion.service.pipeline.parser;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;
import com.argus.rag.common.exception.BusinessException;
/** Reopen disk-backed text for strict UTF-8 / GB18030 decoding; never buffer the full file. */
public final class TextDecodingSupport {
 private TextDecodingSupport(){}
 public static String decode(InputStream input,String failureMessage){
  Path temp=null;
  try {
   temp=Files.createTempFile("argus-text-", ".txt");Files.copy(input,temp,StandardCopyOption.REPLACE_EXISTING);
   byte[] prefix;try(var in=Files.newInputStream(temp)){prefix=in.readNBytes(3);}
   List<Charset> encodings=List.of(StandardCharsets.UTF_8,Charset.forName("GB18030"));int skip=0;
   if(prefix.length>=3 && prefix[0]==(byte)0xef && prefix[1]==(byte)0xbb && prefix[2]==(byte)0xbf)skip=3;
   else if(prefix.length>=2 && prefix[0]==(byte)0xff && prefix[1]==(byte)0xfe){skip=2;encodings=List.of(StandardCharsets.UTF_16LE);}
   else if(prefix.length>=2 && prefix[0]==(byte)0xfe && prefix[1]==(byte)0xff){skip=2;encodings=List.of(StandardCharsets.UTF_16BE);}
   for(Charset encoding:encodings){
    try(var in=Files.newInputStream(temp)) {
     in.skipNBytes(skip);
     var decoder=encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
     try(var reader=new InputStreamReader(in,decoder)){
      var text=new StringBuilder();char[] buffer=new char[4096];int n;
      while((n=reader.read(buffer,0,Math.min(buffer.length,200001-text.length())))>0){text.append(buffer,0,n);if(text.length()>200000)return text.substring(0,200000)+"\n【安全截断：最多提取 200000 字】";}
      return text.toString();
     }
    }catch(CharacterCodingException invalid){ /* Try legacy Chinese encoding. */ }
   }
   throw new IOException("Unsupported encoding");
  }catch(Exception invalid){throw new BusinessException(failureMessage,invalid);}
  finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
 }
}
