package com.argus.rag.college;
import com.argus.rag.auth.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class StrategicTaskCompatibilityTest {
 @Test void legacyPlainTextNoticeReplyDegradesWithoutServerError()throws Exception {
  var model=mock(ChatModel.class);var c=new TaskController(mock(CurrentUserService.class),model,new ObjectMapper());
  ReflectionTestUtils.setField(c,"skills",new ModuleSkillPromptRegistry());
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("请核实院内截止时间。")))));
  var result=c.run("notice",new TaskController.Task("我能报名吗","面向本科生",null)).data();
  assertEquals("YELLOW",result.path("eligibility").path("status").asText());assertTrue(result.path("answer").asText().contains("需核实"));
  assertTrue(result.path("deadlines").isEmpty());
 }
 @Test void interviewLegacyRequestAndAnswerRemainDeserializable()throws Exception {
  var json=new ObjectMapper();
  var request=json.readValue("{\"resumeText\":\"原简历\",\"targetRole\":\"原岗位\",\"interviewType\":\"保研复试\"}",MockInterviewController.Request.class);
  assertNull(request.selectedExperiences());
  var answer=json.readValue("{\"question\":\"问题\",\"category\":\"项目\",\"userAnswer\":\"回答\"}",MockInterviewController.Answer.class);
  assertNull(answer.targetWeakness());
 }
}
