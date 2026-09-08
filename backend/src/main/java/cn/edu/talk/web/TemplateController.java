package cn.edu.talk.web;

import cn.edu.talk.common.*;
import cn.edu.talk.security.Actor;
import cn.edu.talk.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final Db db; private final AuditService audit;
    public TemplateController(Db db,AuditService audit) { this.db=db; this.audit=audit; }
    @GetMapping public Object list(Authentication a) {
        Actor actor=Actor.from(a);
        return db.list("SELECT * FROM record_template"+(actor.admin()?"":" WHERE active=TRUE")+" ORDER BY id");
    }
    @GetMapping("/variables") public Object variables(Authentication a) { Actor.from(a); return new TreeSet<>(TemplateEngine.VARIABLES); }
    @PostMapping @Transactional public Object create(@Valid @RequestBody Inputs.TemplateInput in,Authentication a) {
        Actor actor=Actor.from(a); actor.requireAdmin(); TemplateEngine.validate(in.body());
        long id=db.insert("INSERT INTO record_template(name,title,body,active,created_at) VALUES(?,?,?,?,?)",in.name().strip(),in.title().strip(),in.body(),in.active(),Time.now());
        audit.log(actor,"CREATE_TEMPLATE","TEMPLATE",id,""); return db.one("SELECT * FROM record_template WHERE id=?",id);
    }
    @PutMapping("/{id}") @Transactional public Object update(@PathVariable long id,@Valid @RequestBody Inputs.TemplateInput in,Authentication a) {
        Actor actor=Actor.from(a); actor.requireAdmin(); TemplateEngine.validate(in.body());
        db.list("SELECT id FROM record_template WHERE active=TRUE FOR UPDATE");
        db.one("SELECT id FROM record_template WHERE id=?",id);
        if(!in.active()) ApiException.require(db.count("SELECT COUNT(*) FROM record_template WHERE active=TRUE AND id<>?",id)>0,409,"至少保留一个启用的模板");
        ApiException.require(db.jdbc.update("UPDATE record_template SET name=?,title=?,body=?,active=?,version=version+1 WHERE id=? AND version=?",in.name().strip(),in.title().strip(),in.body(),in.active(),id,in.version())==1,409,"模板已被修改，请刷新");
        audit.log(actor,"UPDATE_TEMPLATE","TEMPLATE",id,""); return db.one("SELECT * FROM record_template WHERE id=?",id);
    }
}
