package com.argus.rag.college;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.auth.CurrentUserService.CurrentUser;
import com.argus.rag.college.mapper.WorkspaceMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceControllerTest {
    private final CurrentUserService users=mock(CurrentUserService.class);
    private final WorkspaceMapper db=mock(WorkspaceMapper.class);
    private final WorkspaceController controller=new WorkspaceController(users,db);
    @Test void loadUsesLoggedInOwner(){
        when(users.getRequiredCurrentUser()).thenReturn(new CurrentUser(27L,"u","A"));
        assertEquals(0, controller.load().data().revision());
        verify(db).load(27L);
    }
    @Test void staleUpdateCannotClobberOtherWrite(){
        when(users.getRequiredCurrentUser()).thenReturn(new CurrentUser(27L,"u","A"));
        var change=new WorkspaceController.Change(2,JsonNodeFactory.instance.objectNode());
        assertEquals(409,controller.save(change).getStatusCode().value());
        verify(db).update(27L,2L,"{}");
    }
    @Test void imageBase64IsNeverPersisted(){
        when(users.getRequiredCurrentUser()).thenReturn(new CurrentUser(27L,"u","A"));
        var state=JsonNodeFactory.instance.objectNode();state.putObject("pages").put("source","data:image/png;base64,AAAA");
        assertThrows(com.argus.rag.common.exception.BusinessException.class,()->controller.save(new WorkspaceController.Change(0,state)));
        verifyNoInteractions(db);
    }
}
