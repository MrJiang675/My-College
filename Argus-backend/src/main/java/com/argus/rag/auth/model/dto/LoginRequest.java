package com.argus.rag.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * @param loginId  登录标识（体验用户为用户名，密码登录支持用户名或邮箱）
 * @param password 可选密码；管理员账号必须提供
 */
public record LoginRequest(
        @NotBlank(message = "登录标识不能为空")
        String loginId,
        String password
) {
}
