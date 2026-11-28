package com.argus.rag.group.controller;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.group.mapper.GroupCollaborationMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class GroupCollaborationControllerTest {
 @Test void statisticsAreScopedToAuthenticatedBusinessUser(){
  var users=mock(CurrentUserService.class);var db=mock(GroupCollaborationMapper.class);
  when(users.requireBusinessUser()).thenReturn(new CurrentUserService.CurrentUser(31L,"u","学生"));
  new GroupCollaborationController(users,db).statistics();verify(db).statistics(31L);
 }
}
