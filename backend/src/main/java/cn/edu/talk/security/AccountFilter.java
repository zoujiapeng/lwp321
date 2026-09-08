package cn.edu.talk.security;

import cn.edu.talk.common.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountFilter extends OncePerRequestFilter {
    private final Db db;
    private final ConcurrentHashMap<String,Window> loginWindows = new ConcurrentHashMap<>();
    private static final class Window { long start; int count; Window(long start) { this.start=start; } }
    public AccountFilter(Db db) { this.db=db; }
    public static void json(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":"+status+",\"message\":\""+message+"\"}");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if ("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getServletPath())) {
            long now=System.currentTimeMillis();
            if (loginWindows.size()>10000) loginWindows.entrySet().removeIf(e -> now-e.getValue().start>300000);
            var window=loginWindows.computeIfAbsent(request.getRemoteAddr(), key -> new Window(now));
            synchronized(window) {
                if (now-window.start>300000) { window.start=now; window.count=0; }
                if (++window.count>30) { json(response,429,"登录请求过于频繁，请5分钟后重试"); return; }
            }
        }
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth!=null && auth.getPrincipal() instanceof TalkUser user) {
            var rows=db.list("SELECT enabled,credential_version,must_change FROM app_user WHERE id=?",user.id);
            if (rows.isEmpty() || !Db.bool(rows.get(0),"enabled") || Db.id(rows.get(0),"credential_version")!=user.credentialVersion) {
                var session=request.getSession(false); if(session!=null) session.invalidate();
                SecurityContextHolder.clearContext(); json(response,401,"账号状态已变更，请重新登录"); return;
            }
            if (Db.bool(rows.get(0),"must_change") && request.getServletPath().startsWith("/api/") && !request.getServletPath().startsWith("/api/auth/")) {
                json(response,403,"首次登录请先修改初始密码"); return;
            }
        }
        chain.doFilter(request,response);
    }
}
