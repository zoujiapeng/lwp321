package cn.edu.talk.web;

import cn.edu.talk.common.*;
import cn.edu.talk.security.Actor;
import cn.edu.talk.service.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/records")
public class RecordController {
    private final RecordService records; private final DocumentExportService documents;
    public RecordController(RecordService records,DocumentExportService documents) { this.records=records; this.documents=documents; }
    @GetMapping public Object list(Authentication a,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String state,
        @RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to,
        @RequestParam(defaultValue="") String followup,@RequestParam(required=false) Long studentId,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return records.list(Actor.from(a),q,state,category,from,to,followup,studentId,page,size);
    }
    @GetMapping("/{id}") public Object detail(@PathVariable long id,Authentication a) { return records.detail(id,Actor.from(a)); }
    @PostMapping("/generate") public Object generate(@Valid @RequestBody Inputs.TalkInput in,Authentication a) { return records.generate(in,Actor.from(a)); }
    @PostMapping public Object create(@Valid @RequestBody Inputs.TalkInput in,Authentication a) { return records.save(null,in,Actor.from(a)); }
    @PutMapping("/{id}") public Object update(@PathVariable long id,@Valid @RequestBody Inputs.TalkInput in,Authentication a) { return records.save(id,in,Actor.from(a)); }
    @PostMapping("/{id}/archive") public Object archive(@PathVariable long id,@RequestBody Inputs.ArchiveInput in,Authentication a) { return records.archive(id,in,Actor.from(a)); }
    @DeleteMapping("/{id}") public Object delete(@PathVariable long id,@RequestParam long version,Authentication a) { records.delete(id,version,Actor.from(a)); return Map.of("message","草稿已删除"); }
    @PostMapping("/{id}/followups") public Object followup(@PathVariable long id,@Valid @RequestBody Inputs.FollowupInput in,Authentication a) { return records.followup(id,in,Actor.from(a)); }
    @GetMapping("/{id}/export") public ResponseEntity<byte[]> export(@PathVariable long id,Authentication a) throws IOException {
        var rows=records.exportable(List.of(id),Actor.from(a));
        return download(documents.docx(rows.get(0)),Db.text(rows.get(0),"record_no")+".docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }
    @PostMapping("/export-batch") public ResponseEntity<byte[]> batch(@Valid @RequestBody Inputs.BatchInput in,Authentication a) throws IOException {
        return download(documents.zip(records.exportable(in.ids(),Actor.from(a))),"谈话记录批量导出.zip","application/zip");
    }
    private ResponseEntity<byte[]> download(byte[] bytes,String name,String type) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(type)).contentLength(bytes.length).body(bytes);
    }
}
