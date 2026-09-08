package cn.edu.talk.web;

import cn.edu.talk.common.*;
import cn.edu.talk.security.Actor;
import cn.edu.talk.service.AuditService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final Db db; private final PasswordEncoder encoder; private final AuditService audit;
    private static final String SAFE="id,username,display_name,role,enabled,must_change,created_at";
    public UserController(Db db,PasswordEncoder encoder,AuditService audit) { this.db=db; this.encoder=encoder; this.audit=audit; }
    @GetMapping public Object list(Authentication a,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String q) {
        Actor.from(a).requireAdmin(); ApiException.require(q.length()<=100,400,"检索词过长");
        var args=List.<Object>of("%"+q+"%","%"+q+"%");
        return db.page("SELECT "+SAFE+" FROM app_user WHERE username LIKE ? OR display_name LIKE ? ORDER BY id DESC","SELECT COUNT(*) FROM app_user WHERE username LIKE ? OR display_name LIKE ?",args,page,size);
    }
    @GetMapping("/teachers") public Object teachers(Authentication a) {
        Actor actor=Actor.from(a);
        return actor.admin()?db.list("SELECT id,display_name FROM app_user WHERE enabled=TRUE ORDER BY id"):
            db.list("SELECT id,display_name FROM app_user WHERE id=?",actor.id());
    }
    @PostMapping @Transactional public Object create(@Valid @RequestBody Inputs.UserInput in,Authentication a) {
        Actor actor=Actor.from(a); actor.requireAdmin(); validRole(in.role()); Inputs.password(in.password());
        long id=db.insert("INSERT INTO app_user(username,password_hash,display_name,role,enabled,created_at) VALUES(?,?,?,?,?,?)",
            in.username(),encoder.encode(in.password()),in.displayName().strip(),in.role(),in.enabled(),Time.now());
        audit.log(actor,"CREATE_USER","USER",id,""); return db.one("SELECT "+SAFE+" FROM app_user WHERE id=?",id);
    }
    @PutMapping("/{id}") @Transactional public Object update(@PathVariable long id,@Valid @RequestBody Inputs.UserInput in,Authentication a) {
        Actor actor=Actor.from(a); actor.requireAdmin(); validRole(in.role());
        db.list("SELECT id FROM app_user WHERE role='ADMIN' FOR UPDATE");
        var row=db.one("SELECT * FROM app_user WHERE id=? FOR UPDATE",id);
        ApiException.require(Db.text(row,"username").equals(in.username()),400,"账号名不可修改");
        ApiException.require(id!=actor.id() || (in.enabled() && "ADMIN".equals(in.role())),400,"不能停用自己或移除自己的管理员权限");
        if("ADMIN".equals(Db.text(row,"role")) && (!in.enabled() || !"ADMIN".equals(in.role())))
            ApiException.require(db.count("SELECT COUNT(*) FROM app_user WHERE role='ADMIN' AND enabled=TRUE AND id<>?",id)>0,409,"至少保留一个启用的管理员");
        db.jdbc.update("UPDATE app_user SET display_name=?,role=?,enabled=?,credential_version=credential_version+1 WHERE id=?",in.displayName().strip(),in.role(),in.enabled(),id);
        audit.log(actor,"UPDATE_USER","USER",id,"角色："+in.role()+"，启用："+in.enabled());
        return db.one("SELECT "+SAFE+" FROM app_user WHERE id=?",id);
    }
    @PostMapping("/{id}/reset-password") @Transactional public Object reset(@PathVariable long id,@Valid @RequestBody Inputs.ResetInput in,Authentication a) {
        Actor actor=Actor.from(a); actor.requireAdmin(); Inputs.password(in.password());
        ApiException.require(id!=actor.id(),400,"请通过修改密码功能修改自己的密码");
        db.one("SELECT id FROM app_user WHERE id=?",id);
        db.jdbc.update("UPDATE app_user SET password_hash=?,must_change=TRUE,credential_version=credential_version+1,failed_attempts=0,locked_until=NULL WHERE id=?",encoder.encode(in.password()),id);
        audit.log(actor,"RESET_PASSWORD","USER",id,""); return Map.of("message","密码已重置，该用户下次登录须修改密码");
    }
    private void validRole(String role) { ApiException.require(Set.of("ADMIN","TEACHER").contains(role),400,"角色不正确"); }
}
