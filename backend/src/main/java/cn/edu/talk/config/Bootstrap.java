package cn.edu.talk.config;

import cn.edu.talk.common.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements CommandLineRunner {
    private final Db db; private final PasswordEncoder encoder; private final String initialPassword;
    public Bootstrap(Db db, PasswordEncoder encoder, @Value("${app.initial-admin-password:}") String initialPassword) {
        this.db=db; this.encoder=encoder; this.initialPassword=initialPassword;
    }
    @Override @Transactional public void run(String... args) throws Exception {
        if(db.count("SELECT COUNT(*) FROM app_user")==0) {
            try { Inputs.password(initialPassword); }
            catch(ApiException ex) { throw new IllegalStateException("首次启动必须设置 APP_ADMIN_PASSWORD（至少12字符，含字母和数字）；建议使用 scripts/start.sh。",ex); }
            db.insert("INSERT INTO app_user(username,password_hash,display_name,role,created_at) VALUES(?,?,?,?,?)",
                "admin",encoder.encode(initialPassword),"系统管理员","ADMIN",Time.now());
        }
        if(db.count("SELECT COUNT(*) FROM record_template")==0) {
            String body="一、谈话背景\n{background}\n\n二、学生陈述\n{studentStatement}\n\n三、教师建议\n{teacherAdvice}\n\n四、双方约定\n{agreement}\n\n五、跟进安排\n计划跟进日期：{followupDate}";
            for(String name: List.of("日常谈话记录","学业指导记录","就业沟通记录")) {
                db.insert("INSERT INTO record_template(name,title,body,created_at) VALUES(?,?,?,?)",name,"师生谈心谈话记录表",body,Time.now());
            }
        }
        // Portable index initialization: MySQL does not support CREATE INDEX IF NOT EXISTS.
        try(var connection=db.jdbc.getDataSource().getConnection()) {
            for(String[] spec: List.of(
                new String[]{"student","idx_student_teacher","teacher_id,active"},
                new String[]{"talk_record","idx_record_teacher","teacher_id,state,occurred_at"},
                new String[]{"talk_record","idx_record_student","student_id,occurred_at"},
                new String[]{"talk_record","idx_record_followup","followup_status,followup_date"},
                new String[]{"followup","idx_followup_record","record_id,created_at"},
                new String[]{"audit_log","idx_audit_time","created_at"})) {
                boolean found=false;
                try(var rs=connection.getMetaData().getIndexInfo(connection.getCatalog(),null,spec[0],false,false)) {
                    while(rs.next()) if(spec[1].equalsIgnoreCase(rs.getString("INDEX_NAME"))) found=true;
                }
                if(!found) db.jdbc.execute("CREATE INDEX "+spec[1]+" ON "+spec[0]+"("+spec[2]+")");
            }
        }
    }
}
