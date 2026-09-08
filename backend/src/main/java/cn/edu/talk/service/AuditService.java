package cn.edu.talk.service;

import cn.edu.talk.common.*;
import cn.edu.talk.security.Actor;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final Db db;
    public AuditService(Db db) { this.db = db; }
    public void log(Actor actor, String action, String type, Long id, String detail) {
        db.insert("INSERT INTO audit_log(actor_id,actor_name,action,target_type,target_id,detail,created_at) VALUES(?,?,?,?,?,?,?)",
            actor == null ? null : actor.id(), actor == null ? "未认证用户" : actor.name(), action, type, id, detail, Time.now());
    }
}
