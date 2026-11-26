package com.argus.rag.college;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.college.mapper.WorkspaceMapper;
import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/college/workspace")
public class WorkspaceController {
    private final CurrentUserService users;
    private final WorkspaceMapper mapper;

    public WorkspaceController(CurrentUserService users, WorkspaceMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    public record Change(long revision, JsonNode state) {}

    @GetMapping
    public ApiResponse<Workspace> load() {
        var row = mapper.load(users.getRequiredCurrentUser().userId());
        return ApiResponse.success(row == null ? new Workspace(0, "{}") : row);
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Long>> save(@RequestBody Change change) {
        long owner = users.getRequiredCurrentUser().userId();
        if (change.revision() < 0 || change.state() == null || !change.state().isObject())
            throw new BusinessException("请输入有效的工作台数据");
        String payload = change.state().toString();
        if (payload.length() > 1000000) throw new BusinessException("工作台数据过大");
        int updated = change.revision() == 0 ? mapper.create(owner, payload)
            : mapper.update(owner, change.revision(), payload);
        if (updated == 0) return ResponseEntity.status(409).body(new ApiResponse<>(false, null, "其他设备已保存，请重新加载"));
        return ResponseEntity.ok(ApiResponse.success(change.revision()+1));
    }
}
