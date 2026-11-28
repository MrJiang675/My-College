package com.argus.rag.college;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class NoticeAnalysisSupportTest {
 private final ObjectMapper json=new ObjectMapper();
 private final NoticeAnalysisSupport support=new NoticeAnalysisSupport(json);
 private final CampusProfile profile=new CampusProfile("测试大学","信息工程学院","信息工程","大二");
 @Test void fabricatedQuotesAndDatesCannotBecomeActionable()throws Exception {
  var input=json.readTree("""
   {"answer":"可以报名","eligibility":{"status":"GREEN","summary":"符合","evidences":["仅限大二"]},
    "deadlines":[{"title":"截止","dateTime":"2026-12-10T17:00:00+08:00","needsVerify":false,"evidenceQuote":"12月10日截止"}],
    "checklist":[{"item":"盖章","required":true,"evidenceQuote":"不存在的原句"}]}
   """);
  var result=support.normalize(input,"请关注学院后续通知。",profile,true);
  assertEquals("YELLOW",result.path("eligibility").path("status").asText());
  assertTrue(result.path("deadlines").get(0).path("needsVerify").asBoolean());
  assertTrue(result.path("checklist").isEmpty());assertFalse(result.path("teacherInquiryScripts").isEmpty());
 }
 @Test void fullSourceTimestampIsExportableButMissingYearIsNot()throws Exception {
  var input=json.readTree("""
   {"answer":"核对原文","deadlines":[{"dateTime":"2026-12-10T17:00:00+08:00","needsVerify":false,"evidenceQuote":"截止2026年12月10日17:00"}]}
   """);
  var result=support.normalize(input,"截止2026年12月10日17:00",profile,true);
  assertFalse(result.path("deadlines").get(0).path("needsVerify").asBoolean());
  var wrongZone=input.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)wrongZone.path("deadlines").get(0)).put("dateTime","2026-12-10T17:00:00Z");
  assertTrue(support.normalize(wrongZone,"截止2026年12月10日17:00",profile,true).path("deadlines").get(0).path("needsVerify").asBoolean());
  assertTrue(support.normalize(input,"12月10日截止",profile,true).path("deadlines").get(0).path("needsVerify").asBoolean());
 }
 @Test void disabledProfileAlwaysYieldsYellowAndLegacyTextSurvives()throws Exception {
  var input=json.readTree("{\"answer\":\"旧前端回答\",\"eligibility\":{\"status\":\"GREEN\",\"evidences\":[\"面向本科生\"]}}");
  var result=support.normalize(input,"面向本科生",profile,false);
  assertEquals("YELLOW",result.path("eligibility").path("status").asText());assertEquals("旧前端回答",result.path("answer").asText());
 }
 @Test void explicitGradeOrCollegeRestrictionOverridesIncorrectGreen()throws Exception {
  String quote="报名仅面向信息工程学院大二、大三本科生";
  var input=json.createObjectNode().put("answer","符合");
  input.putObject("eligibility").put("status","GREEN").putArray("evidences").add(quote);
  assertEquals("RED",support.normalize(input,quote,new CampusProfile("测试大学","信息工程学院","信息工程","大一"),true).path("eligibility").path("status").asText());
  assertEquals("RED",support.normalize(input,quote,new CampusProfile("测试大学","商学院","会计","大二"),true).path("eligibility").path("status").asText());
  assertEquals("GREEN",support.normalize(input,quote,profile,true).path("eligibility").path("status").asText());
 }
}
