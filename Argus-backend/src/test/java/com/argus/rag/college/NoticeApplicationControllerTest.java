package com.argus.rag.college;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.college.mapper.WorkspaceMapper;
import com.argus.rag.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class NoticeApplicationControllerTest {
 @Test void selectedIdsOnlyReadFromCurrentOwnersWorkspace(){
  var users=mock(CurrentUserService.class);var db=mock(WorkspaceMapper.class);var model=mock(ChatModel.class);
  when(users.getRequiredCurrentUser()).thenReturn(new CurrentUserService.CurrentUser(23L,"u","学生"));
  when(db.load(23L)).thenReturn(new Workspace(1,"{\"entries\":[{\"id\":\"mine\",\"text\":\"个人贡献\"}]}"));
  var controller=new NoticeApplicationController(users,db,new ObjectMapper(),model);
  assertThrows(BusinessException.class,()->controller.generate(new NoticeApplicationController.Request("通知",List.of("another-user-entry"),null)));
  verify(db).load(23L);verifyNoInteractions(model);
 }
}
