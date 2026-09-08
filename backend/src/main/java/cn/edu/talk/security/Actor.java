package cn.edu.talk.security;

import cn.edu.talk.common.ApiException;
import org.springframework.security.core.Authentication;

public record Actor(long id, String username, String name, String role) {
    public boolean admin() { return "ADMIN".equals(role); }
    public void requireAdmin() { ApiException.require(admin(), 403, "此操作仅限管理员"); }
    public static Actor from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TalkUser user)) throw new ApiException(401, "请先登录");
        return new Actor(user.id, user.getUsername(), user.displayName, user.role);
    }
}
