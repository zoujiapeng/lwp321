package cn.edu.talk.service;

import cn.edu.talk.common.*;
import cn.edu.talk.security.Actor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordService {
    private final Db db; private final StudentService students; private final AuditService audit;
    public RecordService(Db db,StudentService students,AuditService audit) { this.db=db; this.students=students; this.audit=audit; }
    private Map<String,Object> visible(long id,Actor actor,boolean lock) {
        var record=db.one("SELECT * FROM talk_record WHERE id=?"+(lock?" FOR UPDATE":""),id);
        ApiException.require(actor.admin() || Db.id(record,"teacher_id")==actor.id(),404,"数据不存在或无权访问"); return record;
    }
    public Map<String,Object> detail(long id,Actor actor) {
        var row=visible(id,actor,false);
        var student=db.one("SELECT name,student_no,class_name FROM student WHERE id=?",Db.id(row,"student_id"));
        var owner=db.one("SELECT display_name FROM app_user WHERE id=?",Db.id(row,"teacher_id"));
        var template=db.one("SELECT name,title FROM record_template WHERE id=?",Db.id(row,"template_id"));
        boolean archived="ARCHIVED".equals(Db.text(row,"state"));
        row.put("student_name",archived?row.get("snapshot_student_name"):student.get("name"));
        row.put("student_no",archived?row.get("snapshot_student_no"):student.get("student_no"));
        row.put("class_name",archived?row.get("snapshot_class_name"):student.get("class_name"));
        row.put("teacher_name",archived?row.get("snapshot_teacher_name"):owner.get("display_name"));
        row.put("template_name",archived?row.get("snapshot_template_name"):template.get("name"));
        row.put("document_title",archived?row.get("snapshot_title"):template.get("title"));
        row.put("followups",db.list("SELECT f.*,u.display_name AS teacher_name FROM followup f JOIN app_user u ON u.id=f.teacher_id WHERE f.record_id=? ORDER BY f.id",id));
        audit.log(actor,"VIEW_RECORD","RECORD",id,""); return row;
    }
    public Map<String,Object> list(Actor actor,String q,String state,String category,String from,String to,String followup,Long studentId,int page,int size) {
        String where=" WHERE 1=1"; var args=new ArrayList<Object>();
        if(!actor.admin()) { where+=" AND r.teacher_id=?"; args.add(actor.id()); }
        if(studentId!=null) { where+=" AND r.student_id=?"; args.add(studentId); }
        if(q!=null && !q.isBlank()) {
            ApiException.require(q.length()<=100,400,"检索词过长");
            where+=" AND (r.topic LIKE ? OR COALESCE(r.snapshot_student_name,s.name) LIKE ? OR COALESCE(r.snapshot_student_no,s.student_no) LIKE ? OR r.record_no LIKE ?)";
            for(int i=0;i<4;i++) args.add("%"+q.strip()+"%");
        }
        if(state!=null && !state.isBlank()) { ApiException.require(Set.of("DRAFT","ARCHIVED").contains(state),400,"记录状态不正确"); where+=" AND r.state=?"; args.add(state); }
        if(category!=null && !category.isBlank()) { where+=" AND r.category=?"; args.add(category); }
        String first=Time.date(from),last=Time.date(to);
        if(first!=null && last!=null) ApiException.require(first.compareTo(last)<=0,400,"开始日期不能晚于结束日期");
        if(first!=null) { where+=" AND r.occurred_at>=?"; args.add(first+"T00:00:00"); }
        if(last!=null) { where+=" AND r.occurred_at<=?"; args.add(last+"T23:59:59"); }
        if(followup!=null && !followup.isBlank()) {
            switch(followup) {
                case "open" -> where+=" AND r.followup_status='OPEN'";
                case "done" -> where+=" AND r.followup_status='DONE'";
                case "overdue" -> { where+=" AND r.followup_status='OPEN' AND r.followup_date<?"; args.add(Time.today()); }
                case "today" -> { where+=" AND r.followup_status='OPEN' AND r.followup_date=?"; args.add(Time.today()); }
                default -> throw new ApiException(400,"跟进筛选条件不正确");
            }
        }
        String joined=" FROM talk_record r JOIN student s ON s.id=r.student_id JOIN app_user u ON u.id=r.teacher_id";
        String fields="r.id,r.record_no,r.student_id,r.teacher_id,r.topic,r.category,r.occurred_at,r.state,r.version,r.followup_date,r.followup_status,COALESCE(r.snapshot_student_name,s.name) AS student_name,COALESCE(r.snapshot_student_no,s.student_no) AS student_no,COALESCE(r.snapshot_class_name,s.class_name) AS class_name,COALESCE(r.snapshot_teacher_name,u.display_name) AS teacher_name";
        return db.page("SELECT "+fields+joined+where+" ORDER BY r.occurred_at DESC,r.id DESC","SELECT COUNT(*)"+joined+where,args,page,size);
    }
    private void validate(Inputs.TalkInput in) {
        ApiException.require(Set.of("学业发展","生活适应","人际沟通","就业规划","其他").contains(in.category()),400,"谈话类型不正确");
        ApiException.require(Set.of("面谈","电话","视频","其他").contains(in.mode()),400,"谈话方式不正确");
        String occurred=Time.occurred(in.occurredAt()),followup=Time.date(in.followupDate());
        if(followup!=null) ApiException.require(followup.compareTo(occurred.substring(0,10))>=0,400,"跟进日期不能早于谈话日期");
    }
    private Map<String,String> values(Inputs.TalkInput in,Map<String,Object> student,String teacherName) {
        var values=new LinkedHashMap<String,String>();
        values.put("studentName",Db.text(student,"name")); values.put("studentNo",Db.text(student,"student_no"));
        values.put("className",Db.text(student,"class_name")); values.put("teacherName",teacherName);
        values.put("topic",in.topic()); values.put("occurredAt",Time.occurred(in.occurredAt()).replace('T',' '));
        values.put("place",in.place()); values.put("mode",in.mode()); values.put("durationMinutes",String.valueOf(in.durationMinutes()));
        values.put("background",Inputs.clean(in.background())); values.put("studentStatement",Inputs.clean(in.studentStatement()));
        values.put("teacherAdvice",Inputs.clean(in.teacherAdvice())); values.put("agreement",Inputs.clean(in.agreement()));
        values.put("followupDate",Time.date(in.followupDate())==null?"未设置":Time.date(in.followupDate())); return values;
    }
    public Map<String,Object> generate(Inputs.TalkInput in,Actor actor) {
        validate(in); var student=students.get(in.studentId(),actor);
        ApiException.require(Db.bool(student,"active"),400,"该学生档案已停用");
        requireNotes(in.studentStatement(),in.teacherAdvice(),in.agreement());
        var template=db.one("SELECT * FROM record_template WHERE id=? AND active=TRUE",in.templateId());
        String teacher=Db.text(db.one("SELECT display_name FROM app_user WHERE id=?",actor.id()),"display_name");
        String content=TemplateEngine.render(Db.text(template,"body"),values(in,student,teacher));
        ApiException.require(content.length()<=20000,400,"生成内容超过20000字符，请精简要点或模板");
        return Map.of("content",content,"templateTitle",Db.text(template,"title"),"message","已按要点生成草稿，请逐项核对；没有新增谈话事实");
    }
    @Transactional public Map<String,Object> save(Long id,Inputs.TalkInput in,Actor actor) {
        validate(in); Map<String,Object> old=id==null?null:visible(id,actor,true);
        if(old!=null) {
            ApiException.require("DRAFT".equals(Db.text(old,"state")),409,"已归档记录不可修改，请追加跟进说明");
            ApiException.require(Db.id(old,"version")==in.version(),409,"记录已被其他页面修改，请刷新后重试");
        }
        boolean sameStudent=old!=null && Db.id(old,"student_id")==in.studentId();
        var student=sameStudent?db.one("SELECT * FROM student WHERE id=?",in.studentId()):students.get(in.studentId(),actor);
        ApiException.require(sameStudent || Db.bool(student,"active"),400,"不能为已停用档案新增记录");
        db.one("SELECT id FROM record_template WHERE id=? AND active=TRUE",in.templateId());
        Object[] data={in.studentId(),in.templateId(),in.topic().strip(),in.category(),Time.occurred(in.occurredAt()),in.durationMinutes(),in.place().strip(),in.mode(),Inputs.clean(in.background()),Inputs.clean(in.studentStatement()),Inputs.clean(in.teacherAdvice()),Inputs.clean(in.agreement()),Time.date(in.followupDate()),Inputs.clean(in.content())};
        if(id==null) {
            var args=new ArrayList<>(Arrays.asList(data)); args.add("TH"+Time.today().replace("-","")+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT)); args.add(actor.id()); args.add(Time.now()); args.add(Time.now());
            id=db.insert("INSERT INTO talk_record(student_id,template_id,topic,category,occurred_at,duration_minutes,place,mode,background,student_statement,teacher_advice,agreement,followup_date,content,record_no,teacher_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",args.toArray());
        } else {
            var args=new ArrayList<>(Arrays.asList(data)); args.add(Time.now()); args.add(id); args.add(in.version());
            int changed=db.jdbc.update("UPDATE talk_record SET student_id=?,template_id=?,topic=?,category=?,occurred_at=?,duration_minutes=?,place=?,mode=?,background=?,student_statement=?,teacher_advice=?,agreement=?,followup_date=?,content=?,updated_at=?,version=version+1 WHERE id=? AND version=?",args.toArray());
            ApiException.require(changed==1,409,"记录已变更，请刷新");
        }
        audit.log(actor,"SAVE_DRAFT","RECORD",id,""); return detail(id,actor);
    }
    private void requireNotes(String statement,String advice,String agreement) {
        ApiException.require(!Inputs.clean(statement).isBlank() && !Inputs.clean(advice).isBlank() && !Inputs.clean(agreement).isBlank(),400,"请填写学生陈述、教师建议和双方约定，系统不会代为编造");
    }
    @Transactional public Map<String,Object> archive(long id,Inputs.ArchiveInput in,Actor actor) {
        var row=visible(id,actor,true);
        ApiException.require(in.confirmed(),400,"请确认已核对记录与实际谈话一致");
        ApiException.require("DRAFT".equals(Db.text(row,"state")),409,"记录已经归档，请勿重复提交");
        ApiException.require(Db.id(row,"version")==in.version(),409,"记录已变更，请刷新后重新核对");
        requireNotes(Db.text(row,"student_statement"),Db.text(row,"teacher_advice"),Db.text(row,"agreement"));
        ApiException.require(!Db.text(row,"content").isBlank(),400,"请先生成或填写完整记录正文");
        var student=db.one("SELECT * FROM student WHERE id=?",Db.id(row,"student_id"));
        var teacher=db.one("SELECT display_name FROM app_user WHERE id=?",Db.id(row,"teacher_id"));
        var template=db.one("SELECT name,title FROM record_template WHERE id=?",Db.id(row,"template_id"));
        db.jdbc.update("UPDATE talk_record SET state='ARCHIVED',snapshot_student_name=?,snapshot_student_no=?,snapshot_class_name=?,snapshot_teacher_name=?,snapshot_template_name=?,snapshot_title=?,content_hash=?,archived_at=?,updated_at=?,followup_status=?,version=version+1 WHERE id=?",
            student.get("name"),student.get("student_no"),student.get("class_name"),teacher.get("display_name"),template.get("name"),template.get("title"),hash(Db.text(row,"content")),Time.now(),Time.now(),row.get("followup_date")==null?"NONE":"OPEN",id);
        audit.log(actor,"ARCHIVE_RECORD","RECORD",id,"人工确认；正文SHA-256已保存"); return detail(id,actor);
    }
    public static String hash(String content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    @Transactional public void delete(long id,long version,Actor actor) {
        var row=visible(id,actor,true);
        ApiException.require("DRAFT".equals(Db.text(row,"state")),409,"已归档记录禁止删除");
        ApiException.require(db.jdbc.update("DELETE FROM talk_record WHERE id=? AND version=?",id,version)==1,409,"记录已变更，请刷新");
        audit.log(actor,"DELETE_DRAFT","RECORD",id,"");
    }
    @Transactional public Object followup(long id,Inputs.FollowupInput in,Actor actor) {
        var row=visible(id,actor,true);
        ApiException.require("ARCHIVED".equals(Db.text(row,"state")),409,"请先归档原谈话，再追加跟进记录");
        ApiException.require(Db.id(row,"version")==in.version(),409,"记录已被更新，请刷新后重试");
        String next=Time.date(in.nextDate());
        ApiException.require(in.resolved() || next!=null,400,"未完成跟进时，请设置下一次跟进日期");
        ApiException.require(next==null || next.compareTo(Time.today())>=0,400,"下一次跟进日期不能早于今天");
        ApiException.require(!in.resolved() || next==null,400,"完成跟进时请清空下一次跟进日期");
        db.insert("INSERT INTO followup(record_id,teacher_id,content,next_date,resolved,created_at) VALUES(?,?,?,?,?,?)",id,actor.id(),in.content().strip(),next,in.resolved(),Time.now());
        db.jdbc.update("UPDATE talk_record SET followup_status=?,followup_date=?,version=version+1,updated_at=? WHERE id=?",in.resolved()?"DONE":"OPEN",next,Time.now(),id);
        audit.log(actor,"ADD_FOLLOWUP","RECORD",id,in.resolved()?"完成跟进":"安排继续跟进"); return detail(id,actor);
    }
    public List<Map<String,Object>> exportable(List<Long> ids,Actor actor) {
        ApiException.require(ids.size()>=1 && ids.size()<=50,400,"一次请选择1至50条记录");
        var unique=new LinkedHashSet<>(ids); ApiException.require(unique.size()==ids.size(),400,"导出列表不能包含重复记录");
        for(long id:ids) visible(id,actor,false); // Validate every row before producing any export bytes.
        var rows=new ArrayList<Map<String,Object>>();
        for(long id:ids) { rows.add(detail(id,actor)); audit.log(actor,"EXPORT_RECORD","RECORD",id,""); }
        return rows;
    }
}
