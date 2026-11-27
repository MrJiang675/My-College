
package com.argus.rag.qa.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QaAnswerParserResilienceTest {
 private final QaAnswerParser parser=new QaAnswerParser(new ObjectMapper());
 @Test void fencedAndTrailingComma(){
  var result=parser.parse("说明： ```json\n{\"answered\":true,\"answer\":\"答案\",}\n```");
  assertTrue(result.answered());assertEquals("答案",result.answer());
 }
 @Test void truncatedJsonIsUnverified(){
  var result=parser.parse("{\"answered\":true,\"answer\":");
  assertTrue(result.answer().startsWith("【需核实】"));assertEquals("UNVERIFIED_TEXT",result.reasonCode());
 }
 @Test void plainTextIsUnverified(){assertTrue(parser.parse("原文答复").answer().contains("原文答复"));}
}
