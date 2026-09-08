package cn.edu.talk.service;

import cn.edu.talk.common.*;
import cn.edu.talk.security.Actor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StudentService {
    private final Db db; private final AuditService audit;
    public StudentService(Db db, AuditService audit) { this.db=db; this.audit=audit; }
    public Map<String,Object> get(long id, Actor actor) {
        var row=db.one("SELECT s.*,u.display_name AS teacher_name FROM student s JOIN app_user u ON u.id=s.teacher_id WHERE s.id=?",id);
        ApiException.require(actor.admin() || Db.id(row,"teacher_id")==actor.id(),404,"数据不存在或无权访问");
        return row;
    }
    public Map<String,Object> list(Actor actor,String q,int page,int size,boolean activeOnly) {
        var args=new ArrayList<Object>(); String where=" WHERE 1=1";
        if(!actor.admin()) { where+=" AND s.teacher_id=?"; args.add(actor.id()); }
        if(activeOnly) where+=" AND s.active=TRUE";
        if(q!=null && !q.isBlank()) {
            ApiException.require(q.length()<=100,400,"检索词过长");
            where+=" AND (s.name LIKE ? OR s.student_no LIKE ? OR s.class_name LIKE ?)";
            for(int i=0;i<3;i++) args.add("%"+q.strip()+"%");
        }
        return db.page("SELECT s.*,u.display_name AS teacher_name FROM student s JOIN app_user u ON u.id=s.teacher_id"+where+" ORDER BY s.id DESC",
            "SELECT COUNT(*) FROM student s"+where,args,page,size);
    }
    private long teacher(Long id, Actor actor) {
        long value=actor.admin() ? (id==null ? actor.id() : id) : actor.id();
        ApiException.require(actor.admin() || id==null || id==actor.id(),403,"教师不能把学生分配给其他教师");
        ApiException.require(db.count("SELECT COUNT(*) FROM app_user WHERE id=? AND enabled=TRUE",value)==1,400,"请选择有效的负责教师");
        return value;
    }
    @Transactional public Map<String,Object> save(Long id,Inputs.StudentInput in,Actor actor) {
        long teacherId=teacher(in.teacherId(),actor);
        String phone=Inputs.clean(in.phone());
        ApiException.require(phone.matches("[0-9+() -]{0,30}"),400,"联系电话格式不正确");
        ApiException.require(in.studentNo().matches("[A-Za-z0-9_-]{1,40}"),400,"学号仅可包含字母、数字、下划线和短横线");
        if(id==null) {
            id=db.insert("INSERT INTO student(student_no,name,college,major,class_name,grade,phone,teacher_id,active,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                in.studentNo().strip(),in.name().strip(),Inputs.clean(in.college()),Inputs.clean(in.major()),in.className().strip(),Inputs.clean(in.grade()),phone,teacherId,in.active(),Time.now(),Time.now());
        } else {
            get(id,actor);
            int changed=db.jdbc.update("UPDATE student SET student_no=?,name=?,college=?,major=?,class_name=?,grade=?,phone=?,teacher_id=?,active=?,version=version+1,updated_at=? WHERE id=? AND version=?",
                in.studentNo().strip(),in.name().strip(),Inputs.clean(in.college()),Inputs.clean(in.major()),in.className().strip(),Inputs.clean(in.grade()),phone,teacherId,in.active(),Time.now(),id,in.version());
            ApiException.require(changed==1,409,"学生信息已被修改，请刷新后重试");
        }
        audit.log(actor,"SAVE_STUDENT","STUDENT",id,""); return get(id,actor);
    }
    @Transactional public void delete(long id,long version,Actor actor) {
        get(id,actor);
        ApiException.require(db.count("SELECT COUNT(*) FROM talk_record WHERE student_id=?",id)==0,409,"该学生已有谈话记录，请改为停用档案，不要删除");
        ApiException.require(db.jdbc.update("DELETE FROM student WHERE id=? AND version=?",id,version)==1,409,"学生信息已变更，请刷新");
        audit.log(actor,"DELETE_STUDENT","STUDENT",id,"");
    }
    public static final String[] HEADERS={"学号","姓名","学院","专业","班级","年级","联系电话"};
    public byte[] csv(Actor actor,boolean blank) throws IOException {
        var output=new StringWriter(); output.write('\ufeff');
        try(var printer=new CSVPrinter(output,CSVFormat.DEFAULT.builder().setHeader(HEADERS).get())) {
            if(!blank) {
                String scope=actor.admin()?"":" WHERE teacher_id=?";
                var rows=actor.admin()?db.list("SELECT * FROM student ORDER BY id LIMIT 10001"):db.list("SELECT * FROM student"+scope+" ORDER BY id LIMIT 10001",actor.id());
                ApiException.require(rows.size()<=10000,400,"导出上限为10000条，请分批处理");
                for(var row:rows) {
                    var values=new ArrayList<String>();
                    for(String field:List.of("student_no","name","college","major","class_name","grade","phone")) values.add(csvSafe(Db.text(row,field)));
                    printer.printRecord(values);
                }
                audit.log(actor,"EXPORT_STUDENTS","STUDENT",null,"条数："+rows.size());
            }
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }
    public static String csvSafe(String text) {
        return text.matches("^[\\s]*[=+@-].*") ? "'"+text : text;
    }
    @Transactional public int importCsv(MultipartFile file,Long teacherId,Actor actor) throws IOException {
        ApiException.require(!file.isEmpty() && file.getSize()<=2*1024*1024,400,"请选择不超过2MB的UTF-8 CSV文件");
        long assigned=teacher(teacherId,actor);
        String source=new String(file.getBytes(),StandardCharsets.UTF_8);
        if(source.startsWith("\ufeff")) source=source.substring(1);
        ApiException.require(!source.contains("\ufffd"),400,"CSV文件不是有效的UTF-8文本，请重新另存为UTF-8 CSV");
        var pending=new ArrayList<String[]>(); var seen=new HashSet<String>();
        try(var parser=CSVParser.parse(source,CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
            ApiException.require(parser.getHeaderMap().keySet().containsAll(List.of(HEADERS)),400,"表头不匹配，请先下载导入模板");
            for(var record:parser) {
                ApiException.require(pending.size()<500,400,"一次最多导入500名学生");
                var values=new String[7]; int[] limits={40,60,100,100,80,20,30};
                for(int i=0;i<7;i++) { values[i]=record.get(HEADERS[i]).strip(); ApiException.require(values[i].length()<=limits[i],400,"第"+(record.getRecordNumber()+1)+"行字段过长"); }
                ApiException.require(values[0].matches("[A-Za-z0-9_-]{1,40}") && !values[1].isBlank() && !values[4].isBlank(),400,"第"+(record.getRecordNumber()+1)+"行：学号、姓名和班级必须有效");
                ApiException.require(values[6].matches("[0-9+() -]{0,30}"),400,"第"+(record.getRecordNumber()+1)+"行联系电话格式不正确");
                ApiException.require(seen.add(values[0].toLowerCase(Locale.ROOT)),400,"文件内存在重复学号："+values[0]);
                ApiException.require(db.count("SELECT COUNT(*) FROM student WHERE LOWER(student_no)=LOWER(?)",values[0])==0,409,"学号已存在："+values[0]+"；本次没有导入任何学生");
                pending.add(values);
            }
        } catch(IllegalArgumentException ex) { throw new ApiException(400,"CSV列数或引号格式不正确"); }
        ApiException.require(!pending.isEmpty(),400,"文件没有可导入的学生数据");
        for(var v:pending) db.insert("INSERT INTO student(student_no,name,college,major,class_name,grade,phone,teacher_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",v[0],v[1],v[2],v[3],v[4],v[5],v[6],assigned,Time.now(),Time.now());
        audit.log(actor,"IMPORT_STUDENTS","STUDENT",null,"条数："+pending.size()); return pending.size();
    }
}
