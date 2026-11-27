package com.argus.rag.college;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.engine.youcom.YoucomSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CampusAdaptationTest {
 @Test void unavailableOfficialSearchFallsBackToSuppliedSource() throws Exception {
  var users=mock(CurrentUserService.class);var model=mock(ChatModel.class);var search=mock(YoucomSearchService.class);
  var controller=new TaskController(users,model,new ObjectMapper());
  ReflectionTestUtils.setField(controller,"skills",new ModuleSkillPromptRegistry());
  ReflectionTestUtils.setField(controller,"search",search);
  when(search.search(anyString())).thenThrow(new IllegalStateException("not configured"));
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"answer\":\"需核实认定目录\",\"quotes\":[]}")))));
  var result=controller.run("notice",new TaskController.Task("能加分吗","通知面向大二学生。",null,"CAMPUS_NOTICE",new CampusProfile("清华大学","某学院",null,"大二"),true,"BOTH")).data();
  assertTrue(result.get("needsVerification").toString().contains("综测"));
  assertTrue(result.get("officialEvidence").asText().contains("暂不可用"));
 }
 @Test void noSearchWhenAdaptationDisabled() throws Exception {
  var model=mock(ChatModel.class);var search=mock(YoucomSearchService.class);
  var controller=new TaskController(mock(CurrentUserService.class),model,new ObjectMapper());
  ReflectionTestUtils.setField(controller,"skills",new ModuleSkillPromptRegistry());
  ReflectionTestUtils.setField(controller,"search",search);
  when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"answer\":\"请提供材料\",\"quotes\":[]}")))));
  controller.run("notice",new TaskController.Task("需要哪些材料","",null,"CAMPUS_NOTICE",new CampusProfile("清华大学",null,null,null),false,"BOTH"));
  verifyNoInteractions(search);
 }
}
