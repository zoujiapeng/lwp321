package cn.edu.talk.web;

import cn.edu.talk.common.*;
import cn.edu.talk.security.Actor;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final Db db;
    public DashboardController(Db db) { this.db=db; }
    private long count(String condition,Actor actor,Object... more) {
        var args=new ArrayList<Object>(); String scope="";
        if(!actor.admin()) { scope=" AND teacher_id=?"; args.add(actor.id()); }
        args.addAll(Arrays.asList(more)); return db.count("SELECT COUNT(*) FROM talk_record WHERE 1=1"+scope+condition,args.toArray());
    }
    @GetMapping("/dashboard") public Object dashboard(Authentication auth) {
        Actor actor=Actor.from(auth); var out=new LinkedHashMap<String,Object>();
        out.put("students",actor.admin()?db.count("SELECT COUNT(*) FROM student WHERE active=TRUE"):db.count("SELECT COUNT(*) FROM student WHERE active=TRUE AND teacher_id=?",actor.id()));
        out.put("records",count("",actor)); out.put("drafts",count(" AND state='DRAFT'",actor)); out.put("archived",count(" AND state='ARCHIVED'",actor));
        out.put("openFollowups",count(" AND followup_status='OPEN'",actor));
        out.put("overdue",count(" AND followup_status='OPEN' AND followup_date<?",actor,Time.today()));
        out.put("today",count(" AND followup_status='OPEN' AND followup_date=?",actor,Time.today()));
        out.put("thisMonth",count(" AND state='ARCHIVED' AND occurred_at LIKE ?",actor,Time.today().substring(0,7)+"%"));
        var args=new ArrayList<Object>(); String scope=" WHERE state='ARCHIVED'";
        if(!actor.admin()) { scope+=" AND teacher_id=?"; args.add(actor.id()); }
        out.put("categories",db.list("SELECT category,COUNT(*) AS total FROM talk_record"+scope+" GROUP BY category ORDER BY total DESC",args.toArray()));
        var months=new ArrayList<Map<String,Object>>();
        for(int i=5;i>=0;i--) { String month=LocalDate.now(Time.ZONE).minusMonths(i).toString().substring(0,7); months.add(Map.of("month",month,"total",count(" AND state='ARCHIVED' AND occurred_at LIKE ?",actor,month+"%"))); }
        out.put("months",months); out.put("todayDate",Time.today()); return out;
    }
    @GetMapping("/audit") public Object audit(Authentication auth,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String q) {
        Actor.from(auth).requireAdmin(); ApiException.require(q.length()<=100,400,"检索词过长");
        var args=List.<Object>of("%"+q+"%","%"+q+"%");
        return db.page("SELECT * FROM audit_log WHERE actor_name LIKE ? OR action LIKE ? ORDER BY id DESC","SELECT COUNT(*) FROM audit_log WHERE actor_name LIKE ? OR action LIKE ?",args,page,size);
    }
}
