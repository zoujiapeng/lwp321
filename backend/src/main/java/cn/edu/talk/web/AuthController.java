package cn.edu.talk.web;

import cn.edu.talk.common.*;
import cn.edu.talk.security.*;
import cn.edu.talk.service.AuditService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final Db db; private final PasswordEncoder encoder; private final AuditService audit;
    public AuthController(Db db, PasswordEncoder encoder, AuditService audit) { this.db=db; this.encoder=encoder; this.audit=audit; }
    @GetMapping("/health") public Map<String,String> health() { db.count("SELECT 1"); return Map.of("status","UP"); }
    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/auth/me") public Map<String,Object> me(Authentication auth) {
        Actor actor=Actor.from(auth);
        return db.one("SELECT id,username,display_name,role,must_change FROM app_user WHERE id=?",actor.id());
    }
    @PostMapping("/auth/password") @Transactional
    public Map<String,String> password(@Valid @RequestBody Inputs.PasswordInput input, Authentication auth, HttpServletRequest request) {
        Actor actor=Actor.from(auth); Inputs.password(input.newPassword());
        var user=db.one("SELECT * FROM app_user WHERE id=? FOR UPDATE",actor.id());
        ApiException.require(encoder.matches(input.oldPassword(),Db.text(user,"password_hash")),400,"原密码不正确");
        ApiException.require(!encoder.matches(input.newPassword(),Db.text(user,"password_hash")),400,"新密码不能与原密码相同");
        db.jdbc.update("UPDATE app_user SET password_hash=?,must_change=FALSE,credential_version=credential_version+1,failed_attempts=0,locked_until=NULL WHERE id=?",encoder.encode(input.newPassword()),actor.id());
        audit.log(actor,"CHANGE_PASSWORD","USER",actor.id(),"");
        request.getSession().invalidate(); SecurityContextHolder.clearContext();
        return Map.of("message","密码已修改，请使用新密码重新登录");
    }
}
