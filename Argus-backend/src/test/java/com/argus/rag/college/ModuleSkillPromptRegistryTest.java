package com.argus.rag.college;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleSkillPromptRegistryTest {
 private final ModuleSkillPromptRegistry registry=new ModuleSkillPromptRegistry();
 @Test void missingProfileSafe(){
  String text=registry.render(new ModuleContext("CAMPUS_NOTICE",new CampusProfile(null,null,null,null),true,null));
  assertTrue(text.contains("需核实"));assertTrue(text.contains("未填写"));assertFalse(text.contains("null"));
 }
 @Test void adaptationCanBeDisabled(){
  String text=registry.render(new ModuleContext("LEARNING",new CampusProfile("私密学校",null,null,null),false,null));
  assertFalse(text.contains("私密学校"));assertTrue(text.contains("跨专业"));
 }
 @Test void oldChatRequestStillWorks() throws Exception {
  var request=new com.fasterxml.jackson.databind.ObjectMapper().readValue("{\"sessionId\":1,\"message\":\"你好\",\"toolMode\":\"CHAT\"}",
       com.argus.rag.assistant.model.dto.chat.AssistantChatRequest.class);
  assertNull(request.moduleCode());assertEquals(1L,request.sessionId());
 }
}
