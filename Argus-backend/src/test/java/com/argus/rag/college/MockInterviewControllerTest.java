package com.argus.rag.college;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.AssistantMessage;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MockInterviewControllerTest {
 private MockInterviewController controller()throws Exception{return new MockInterviewController(mock(CurrentUserService.class),mock(ChatModel.class),new ObjectMapper());}
 @Test void parsesFencedQuestionsAndNormalizesIds()throws Exception {
  var model=mock(ChatModel.class);var c=new MockInterviewController(mock(CurrentUserService.class),model,new ObjectMapper());
  String question="{\"category\":\"项目\",\"question\":\"本人做了什么？\",\"focusPoint\":\"证据\"}";
  String text="```json\n{\"questions\":["+String.join(",",java.util.Collections.nCopies(5,question))+"]}\n```";
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
  var result=c.generate(new MockInterviewController.Request("本人完成标定实验","电气工程","保研复试",null,null)).data();
  assertEquals(5,result.path("questions").size());assertEquals("q5",result.path("questions").get(4).path("id").asText());
 }
 @Test void rejectsIncompleteOrOutOfRangeScores()throws Exception {
  var c=controller();assertThrows(BusinessException.class,()->c.parseResult("{\"overallScore\":120}",true));
  assertThrows(BusinessException.class,()->c.parseResult("模型超时",false));
 }
 @Test void rejectsEmptyAnswerSheetBeforeCallingModel()throws Exception {
  assertThrows(BusinessException.class,()->controller().evaluate(new MockInterviewController.Request("项目经历","方向","综合答辩",null,List.of())));
 }
 @Test void scoreUsesFiveAnswersAndTreatsBlankAnswersAsZero()throws Exception {
  var json=new ObjectMapper();var model=mock(ChatModel.class);var c=new MockInterviewController(mock(CurrentUserService.class),model,json);
  var report=json.createObjectNode().put("overallScore",80).put("summaryLevel","良好");
  var dimensions=report.putArray("dimensions");for(String name:List.of("表达逻辑","经历真实度与证据感","专业深度","目标匹配度"))dimensions.addObject().put("name",name).put("score",80);
  var feedback=report.putArray("questionFeedbacks");for(int i=0;i<5;i++)feedback.addObject().put("score",80).put("strengths","具体").put("weaknesses","补证据").put("improvedAnswerSample","已知贡献");
  report.putArray("actionableAdvice").add("整理证据").add("复习原理").add("录音训练");
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(report.toString())))));
  var answers=java.util.stream.IntStream.range(0,5).mapToObj(i->new MockInterviewController.Answer("问题","项目",i==0?"本人完成标定":"")).toList();
  var actual=c.evaluate(new MockInterviewController.Request("课程项目","实习","实习技术面",null,answers)).data();
  assertEquals(16,actual.path("overallScore").asInt());assertEquals(0,actual.path("questionFeedbacks").get(4).path("score").asInt());
  var captor=org.mockito.ArgumentCaptor.forClass(Prompt.class);verify(model).call(captor.capture());
  assertEquals(4096,captor.getValue().getOptions().getMaxTokens());
 }
 @Test void incompleteQuestionListGetsOnlyOneBoundedRepair()throws Exception {
  var model=mock(ChatModel.class);var c=new MockInterviewController(mock(CurrentUserService.class),model,new ObjectMapper());
  String one="{\"category\":\"贡献\",\"question\":\"你完成了什么？\",\"focusPoint\":\"证据\"}";
  String full="{\"questions\":["+String.join(",",java.util.Collections.nCopies(5,one))+"]}";
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"questions\":[]}")))),new ChatResponse(List.of(new Generation(new AssistantMessage(full)))));
  assertEquals(5,c.generate(new MockInterviewController.Request("本人完成清洗","实习","实习技术/业务面",null,null)).data().path("questions").size());
  verify(model,times(2)).call(any(Prompt.class));
 }
}
