package cn.edu.talk.web;

import cn.edu.talk.common.*;
import cn.edu.talk.security.Actor;
import cn.edu.talk.service.StudentService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService students;
    public StudentController(StudentService students) { this.students=students; }
    @GetMapping public Object list(Authentication a,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="false") boolean activeOnly) {
        return students.list(Actor.from(a),q,page,size,activeOnly);
    }
    @GetMapping("/{id}") public Object get(@PathVariable long id,Authentication a) { return students.get(id,Actor.from(a)); }
    @PostMapping public Object create(@Valid @RequestBody Inputs.StudentInput in,Authentication a) { return students.save(null,in,Actor.from(a)); }
    @PutMapping("/{id}") public Object update(@PathVariable long id,@Valid @RequestBody Inputs.StudentInput in,Authentication a) { return students.save(id,in,Actor.from(a)); }
    @DeleteMapping("/{id}") public Object delete(@PathVariable long id,@RequestParam long version,Authentication a) { students.delete(id,version,Actor.from(a)); return Map.of("message","已删除"); }
    @PostMapping("/import") public Object importCsv(@RequestParam MultipartFile file,@RequestParam(required=false) Long teacherId,Authentication a) throws IOException {
        int count=students.importCsv(file,teacherId,Actor.from(a)); return Map.of("count",count,"message","成功导入"+count+"名学生");
    }
    @GetMapping("/export") public ResponseEntity<byte[]> export(Authentication a) throws IOException { return csv(students.csv(Actor.from(a),false),"students.csv"); }
    @GetMapping("/import-template") public ResponseEntity<byte[]> template(Authentication a) throws IOException { return csv(students.csv(Actor.from(a),true),"students-template.csv"); }
    private ResponseEntity<byte[]> csv(byte[] bytes,String name) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(bytes);
    }
}
