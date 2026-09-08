package cn.edu.talk.service;

import cn.edu.talk.common.Db;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

/** Standard OOXML, inline paragraphs and short metadata rows are used for WPS/Word compatibility. */
@Service
public class DocumentExportService {
    private void text(XWPFParagraph paragraph,String value,int size,boolean bold) {
        var run=paragraph.createRun(); run.setFontFamily("Times New Roman");
        run.setFontFamily("宋体",XWPFRun.FontCharRange.eastAsia); run.setFontSize(size); run.setBold(bold); run.setText(value);
    }
    private XWPFParagraph paragraph(XWPFDocument doc,String value,boolean heading) {
        var p=doc.createParagraph(); p.setSpacingBetween(1.5); p.setSpacingAfter(80);
        if(heading) { p.setKeepNext(true); p.setSpacingBefore(140); }
        else p.setIndentationFirstLine(480);
        var properties=p.getCTP().isSetPPr()?p.getCTP().getPPr():p.getCTP().addNewPPr();
        properties.addNewWidowControl().setVal(true); text(p,value,12,heading); return p;
    }
    public byte[] docx(Map<String,Object> row) throws IOException {
        try(var doc=new XWPFDocument(); var output=new ByteArrayOutputStream()) {
            var section=doc.getDocument().getBody().addNewSectPr();
            var page=section.addNewPgSz(); page.setW(BigInteger.valueOf(11906)); page.setH(BigInteger.valueOf(16838));
            var margin=section.addNewPgMar(); margin.setTop(BigInteger.valueOf(1276)); margin.setBottom(BigInteger.valueOf(1276));
            margin.setLeft(BigInteger.valueOf(1440)); margin.setRight(BigInteger.valueOf(1440)); margin.setFooter(BigInteger.valueOf(600)); margin.setHeader(BigInteger.valueOf(600));
            var title=doc.createParagraph(); title.setAlignment(ParagraphAlignment.CENTER); title.setKeepNext(true);
            text(title,Db.text(row,"document_title"),18,true);
            var info=doc.createParagraph(); info.setAlignment(ParagraphAlignment.CENTER); info.setKeepNext(true);
            boolean archived="ARCHIVED".equals(Db.text(row,"state"));
            text(info,Db.text(row,"record_no")+"  |  "+(archived?"已核对归档":"草稿 · 尚未核对归档"),10,false);
            var table=doc.createTable(4,4); table.setWidth("100%"); table.setTableAlignment(TableRowAlign.CENTER);
            String[][] data={{"学生姓名",Db.text(row,"student_name"),"学号",Db.text(row,"student_no")},
                {"班级",Db.text(row,"class_name"),"谈话教师",Db.text(row,"teacher_name")},
                {"谈话时间",Db.text(row,"occurred_at").replace('T',' '),"时长",Db.text(row,"duration_minutes")+"分钟"},
                {"地点",Db.text(row,"place"),"方式",Db.text(row,"mode")}};
            for(int r=0;r<4;r++) {
                table.getRow(r).setCantSplitRow(true);
                for(int c=0;c<4;c++) {
                    var cell=table.getRow(r).getCell(c); cell.setWidth(c%2==0?"1350":"3100"); cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                    var p=cell.getParagraphs().get(0); p.setSpacingBetween(1.3); p.setSpacingBefore(70); p.setSpacingAfter(70);
                    text(p,data[r][c],11,c%2==0);
                }
            }
            paragraph(doc,"谈话主题："+Db.text(row,"topic"),true);
            paragraph(doc,"谈话类型："+Db.text(row,"category"),false);
            String body=Db.text(row,"content");
            if(body.isBlank()) body="正文尚未填写。本文件为草稿，不应作为正式谈话记录使用。";
            for(String line:body.split("\\R",-1)) {
                if(line.isBlank()) continue;
                paragraph(doc,line,line.matches("^[一二三四五六七八九十]+、.*"));
            }
            var sign=paragraph(doc,"教师签字：________________    学生签字：________________",false); sign.setSpacingBefore(240); sign.setIndentationFirstLine(0);
            var notice=doc.createParagraph(); text(notice,"签名栏由相关人员实际签署；本系统不生成或代签签名。",10,false);
            if(archived) {
                var hash=doc.createParagraph(); text(hash,"归档时间："+Db.text(row,"archived_at").replace('T',' '),10,false);
                text(doc.createParagraph(),"正文校验值（SHA-256）："+Db.text(row,"content_hash"),8,false);
            }
            Object followups=row.get("followups");
            if(followups instanceof List<?> entries && !entries.isEmpty()) {
                paragraph(doc,"后续跟进（独立追加，不改写原归档正文）",true);
                for(Object entry:entries) {
                    @SuppressWarnings("unchecked") var f=(Map<String,Object>)entry;
                    paragraph(doc,Db.text(f,"created_at").replace('T',' ')+"  "+Db.text(f,"teacher_name")+"  "+(Db.bool(f,"resolved")?"完成跟进":"继续跟进"),true);
                    for(String line:Db.text(f,"content").split("\\R")) paragraph(doc,line,false);
                    if(f.get("next_date")!=null) paragraph(doc,"下一次跟进日期："+Db.text(f,"next_date"),false);
                }
            }
            var footer=doc.createFooter(HeaderFooterType.DEFAULT).createParagraph(); footer.setAlignment(ParagraphAlignment.CENTER);
            text(footer,archived?"已归档 · 第 ":"草稿 · 第 ",9,false); footer.getCTP().addNewFldSimple().setInstr("PAGE"); text(footer," 页",9,false);
            doc.getProperties().getCoreProperties().setTitle(Db.text(row,"document_title"));
            doc.getProperties().getCoreProperties().setCreator("师生谈心谈话记录生成系统");
            doc.write(output); return output.toByteArray();
        }
    }
    public byte[] zip(List<Map<String,Object>> records) throws IOException {
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)) {
            for(var record:records) {
                zip.putNextEntry(new ZipEntry(Db.text(record,"record_no")+".docx")); zip.write(docx(record)); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
