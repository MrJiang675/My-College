package com.argus.rag.group.controller;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.group.mapper.GroupCollaborationMapper;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/groups/collaboration")
public class GroupCollaborationController {
    private final CurrentUserService users;
    private final GroupCollaborationMapper mapper;
    public GroupCollaborationController(CurrentUserService users,GroupCollaborationMapper mapper){this.users=users;this.mapper=mapper;}
    @GetMapping public ApiResponse<List<Map<String,Object>>> statistics(){return ApiResponse.success(mapper.statistics(users.requireBusinessUser().userId()));}
}
