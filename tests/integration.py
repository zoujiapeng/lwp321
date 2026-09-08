#!/usr/bin/env python3
"""Disposable end-to-end API tests. Never point TEST_DB_URL at a real database.
Uses only Python's standard library. A local temporary H2 database is the default.
CI can explicitly select its disposable MySQL database through TEST_DB_* variables.
"""
from __future__ import annotations
import concurrent.futures
import datetime as dt
import hashlib
import http.cookiejar
import io
import json
import os
from pathlib import Path
import platform
import shutil
import socket
import statistics
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
INITIAL = "DisposableStart123!"
PASSWORD = "DisposableChanged123!"
RESULTS: list[dict] = []


def require(condition: bool, message: str = "断言失败") -> None:
    if not condition:
        raise AssertionError(message)


def check(name: str, function):
    started = time.perf_counter()
    try:
        result = function()
        RESULTS.append({"name": name, "passed": True, "duration_ms": round((time.perf_counter() - started) * 1000, 2)})
        print("PASS", name, flush=True)
        return result
    except Exception as error:
        RESULTS.append({"name": name, "passed": False, "error": str(error), "duration_ms": round((time.perf_counter() - started) * 1000, 2)})
        print("FAIL", name, str(error), flush=True)
        raise


class Client:
    def __init__(self, base: str):
        self.base = base
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.csrf = None

    def request(self, path: str, method: str = "GET", body=None, expected: int = 200,
                protect: bool = True, content_type: str | None = None):
        headers = {}
        if method not in ("GET", "HEAD") and protect:
            if self.csrf is None:
                self.csrf = self.request("/auth/csrf")
            headers[self.csrf["headerName"]] = self.csrf["token"]
        if body is not None:
            if not isinstance(body, bytes):
                body = json.dumps(body, ensure_ascii=False).encode("utf-8")
                content_type = "application/json"
            headers["Content-Type"] = content_type or "application/octet-stream"
        request = urllib.request.Request(self.base + "/api" + path, data=body, method=method, headers=headers)
        try:
            response = self.opener.open(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        raw = response.read()
        status = response.code
        if status == 401 and path != "/auth/login":
            self.csrf = None
        content = json.loads(raw) if "application/json" in response.headers.get("Content-Type", "") else raw
        require(status == expected, f"{method} {path}: 预期{expected}，实际{status}: {str(content)[:250]}")
        return content

    def call(self, name: str, path: str, method="GET", body=None, expected=200, **kwargs):
        return check(name, lambda: self.request(path, method, body, expected, **kwargs))

    def login(self, name: str, username: str, password: str, expected=200):
        payload = urllib.parse.urlencode({"username": username, "password": password}).encode()
        result = self.call(name, "/auth/login", "POST", payload, expected,
                           content_type="application/x-www-form-urlencoded")
        self.csrf = None
        return result

    def change(self):
        self.request("/auth/password", "POST", {"oldPassword": INITIAL, "newPassword": PASSWORD})
        self.csrf = None

    def csv(self, source: str, teacher: int | None = None, expected=200):
        boundary = "---DisposableTalkBoundary1937"
        data = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"students.csv\"\r\n"
                f"Content-Type: text/csv\r\n\r\n{source}\r\n")
        if teacher is not None:
            data += f"--{boundary}\r\nContent-Disposition: form-data; name=\"teacherId\"\r\n\r\n{teacher}\r\n"
        data += f"--{boundary}--\r\n"
        return self.request("/students/import", "POST", data.encode("utf-8"), expected,
                            content_type="multipart/form-data; boundary=" + boundary)


class Application:
    def __init__(self):
        self.temp = tempfile.TemporaryDirectory(prefix="talk-test-")
        self.work = Path(self.temp.name)
        (self.work / "runtime").mkdir()
        self.jar = Path(os.environ.get("TEST_JAR", ROOT / "backend/target/talk-records.jar")).resolve()
        require(self.jar.is_file(), "先执行 Maven verify 生成可执行JAR")
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            self.port = sock.getsockname()[1]
        self.base = f"http://127.0.0.1:{self.port}"
        self.mysql = bool(os.environ.get("TEST_DB_URL"))
        if self.mysql:
            require("talk_test" in os.environ["TEST_DB_URL"], "安全限制：TEST_DB_URL仅允许命名包含talk_test的可丢弃数据库")
        self.process = None
        self.log = None

    def start(self):
        env = dict(os.environ, APP_ADMIN_PASSWORD=INITIAL, SERVER_PORT=str(self.port), SERVER_ADDRESS="127.0.0.1")
        for key in ("DB_URL", "DB_USER", "DB_PASSWORD"):
            env.pop(key, None)
            if os.environ.get("TEST_" + key):
                env[key] = os.environ["TEST_" + key]
        self.log = open(self.work / "application.log", "ab")
        self.process = subprocess.Popen(["java", "-Xms128m", "-Xmx512m", "-jar", str(self.jar)], cwd=self.work, env=env, stdout=self.log, stderr=subprocess.STDOUT)
        for _ in range(160):
            if self.process.poll() is not None:
                raise RuntimeError((self.work / "application.log").read_text(errors="replace")[-8000:])
            try:
                if Client(self.base).request("/health")["status"] == "UP":
                    time.sleep(0.8)
                    return
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(0.25)
        raise TimeoutError("应用启动超时")

    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            self.process.wait(timeout=45)
        if self.log:
            self.log.close()

    def close(self):
        self.stop()
        self.temp.cleanup()


def student_payload(number, name, teacher):
    return {"studentNo": number, "name": name, "college": "测试学院", "major": "计算机科学与技术", "className": "测试2401班", "grade": "2024级", "phone": "", "teacherId": teacher, "active": True, "version": 0}


def student_edit(row, **changes):
    value = student_payload(row["student_no"], row["name"], row["teacher_id"])
    value.update({"college": row["college"], "major": row["major"], "className": row["class_name"], "grade": row["grade"], "phone": row["phone"], "active": bool(row["active"]), "version": row["version"]})
    value.update(changes)
    return value


def main():
    app = Application()
    evidence = {"database": "MySQL 8.4 (CI service)" if app.mysql else "H2 file (MySQL mode)",
                "executed_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
                "platform": platform.platform(), "tests": RESULTS, "scope": "全新临时数据库与虚构测试数据，不代表校内上线或大规模用户试用"}
    status = 0
    try:
        app.start()
        anon = Client(app.base)
        anon.call("健康检查", "/health")
        anon.call("未登录读取学生被拒绝", "/students", expected=401)
        anon.call("无CSRF令牌登录被拒绝", "/auth/login", "POST", b"username=admin&password=wrong", expected=403, protect=False, content_type="application/x-www-form-urlencoded")
        admin = Client(app.base)
        admin.login("错误密码不能登录", "admin", "WrongPassword123", 401)
        admin.login("正确初始密码登录", "admin", INITIAL)
        admin.call("首次登录被要求改密", "/students", expected=403)
        admin.call("过短的新密码被拒绝", "/auth/password", "POST", {"oldPassword": INITIAL, "newPassword": "abc123"}, 400)
        check("首次修改密码成功", admin.change)
        admin.call("改密后旧会话失效", "/auth/me", expected=401)
        admin.login("修改后重新登录", "admin", PASSWORD)
        users = []
        clients = []
        for suffix in ("a", "b"):
            user = admin.call("创建教师" + suffix, "/users", "POST", {"username": "teacher_" + suffix, "displayName": "测试教师" + suffix.upper(), "role": "TEACHER", "enabled": True, "password": INITIAL})
            users.append(user)
            client = Client(app.base)
            client.login("教师" + suffix + "初始登录", user["username"], INITIAL)
            check("教师" + suffix + "修改初始密码", client.change)
            client.login("教师" + suffix + "新密码登录", user["username"], PASSWORD)
            clients.append(client)
        a, b = clients
        sa = a.call("教师建立负责学生档案", "/students", "POST", student_payload("TEST001", "测试学生甲", users[0]["id"]))
        sb = b.call("另一教师建立独立档案", "/students", "POST", student_payload("TEST002", "测试学生乙", users[1]["id"]))
        a.call("教师不能分配给其他教师", "/students", "POST", student_payload("TEST003", "测试学生丙", users[1]["id"]), 403)
        b.call("跨教师读取学生被拒绝", "/students/" + str(sa["id"]), expected=404)
        a.call("教师不能访问账号管理", "/users", expected=403)
        a.call("教师不能访问审计日志", "/audit", expected=403)
        check("学生列表仅返回负责档案", lambda: require(a.request("/students")["total"] == 1))
        a.call("重复学号被拒绝", "/students", "POST", student_payload("TEST001", "重复", users[0]["id"]), 409)
        a.call("无效分页被拒绝", "/students?page=0", expected=400)
        check("SQL注入文本不能绕过查询", lambda: require(a.request("/students?q=" + urllib.parse.quote("' OR 1=1 --"))["total"] == 0))
        templates = a.request("/templates")
        now = dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
        yesterday = (now - dt.timedelta(days=2)).replace(hour=10, minute=0, second=0, microsecond=0)
        payload = {"studentId": sa["id"], "templateId": templates[0]["id"], "topic": "课程学习计划沟通", "category": "学业发展", "occurredAt": yesterday.isoformat()[:19], "durationMinutes": 30, "place": "测试办公室", "mode": "面谈", "background": "阶段性学习沟通", "studentStatement": "学生表示课程任务较多，希望调整学习计划。", "teacherAdvice": "教师建议列出本周任务，按截止日期逐项完成。", "agreement": "学生整理任务清单，教师在约定日期查看落实情况。", "followupDate": (now.date() - dt.timedelta(days=1)).isoformat(), "content": "", "version": 0}
        a.call("缺少实际陈述时拒绝生成", "/records/generate", "POST", dict(payload, studentStatement=""), 400)
        a.call("Word不支持的控制字符被拒绝", "/records/generate", "POST", dict(payload, studentStatement="错误\u0001内容"), 400)
        a.call("未来谈话时间被拒绝", "/records/generate", "POST", dict(payload, occurredAt="2099-01-01T10:00:00"), 400)
        a.call("跟进日期早于谈话被拒绝", "/records/generate", "POST", dict(payload, followupDate="2000-01-01"), 400)
        generated = a.call("依据实际要点生成记录", "/records/generate", "POST", payload)
        check("生成文本保留原始事实", lambda: require(all(payload[key] in generated["content"] for key in ("studentStatement", "teacherAdvice", "agreement"))))
        literal = a.request("/records/generate", "POST", dict(payload, studentStatement="<script>alert(1)</script> ${7*7} {teacherAdvice}"))
        check("模板不执行脚本或二次替换", lambda: require("<script>alert(1)</script> ${7*7} {teacherAdvice}" in literal["content"]))
        payload["content"] = generated["content"]
        record = a.call("保存完整草稿", "/records", "POST", payload)
        rid = record["id"]
        b.call("其他教师无法读取记录", f"/records/{rid}", expected=404)
        b.call("其他教师无法导出记录", f"/records/{rid}/export", expected=404)
        a.call("无CSRF令牌修改被拒绝", f"/records/{rid}", "PUT", payload, 403, protect=False)
        record = a.call("草稿更新成功", f"/records/{rid}", "PUT", dict(payload, topic="课程学习计划沟通（核对版）"))
        a.call("过期版本不能覆盖新草稿", f"/records/{rid}", "PUT", payload, 409)
        a.call("未人工确认不能归档", f"/records/{rid}/archive", "POST", {"version": record["version"], "confirmed": False}, 400)
        a.call("未归档记录不能追加跟进", f"/records/{rid}/followups", "POST", {"version": record["version"], "content": "测试跟进", "resolved": True}, 409)
        record = a.call("人工核对归档成功", f"/records/{rid}/archive", "POST", {"version": record["version"], "confirmed": True})
        original_hash = record["content_hash"]
        check("归档正文SHA256匹配", lambda: require(original_hash == hashlib.sha256(payload["content"].encode()).hexdigest()))
        a.call("已归档正文不能改写", f"/records/{rid}", "PUT", dict(payload, version=record["version"]), 409)
        a.call("已归档记录不能删除", f"/records/{rid}?version={record['version']}", "DELETE", expected=409)
        a.call("重复归档被拒绝", f"/records/{rid}/archive", "POST", {"version": record["version"], "confirmed": True}, 409)
        check("逾期跟进出现在筛选结果", lambda: require(a.request("/records?followup=overdue")["total"] == 1))
        a.call("未完成跟进必须指定下次日期", f"/records/{rid}/followups", "POST", {"content": "已联系", "resolved": False, "version": record["version"]}, 400)
        record = a.call("追加跟进并完成", f"/records/{rid}/followups", "POST", {"content": "学生已按约定提交清单，教师核对后结束本轮跟进。", "resolved": True, "version": record["version"]})
        check("跟进完成不改写归档正文", lambda: require(record["content_hash"] == original_hash and record["followup_status"] == "DONE" and len(record["followups"]) == 1))
        sa = a.call("修改学生姓名", f"/students/{sa['id']}", "PUT", student_edit(sa, name="测试学生甲（现名）"))
        check("历史归档姓名快照保持原样", lambda: require(a.request(f"/records/{rid}")["student_name"] == "测试学生甲"))
        a.call("有历史记录不能删除学生", f"/students/{sa['id']}?version={sa['version']}", "DELETE", expected=409)
        docx = a.call("单份Word导出", f"/records/{rid}/export")
        def verify_docx():
            with zipfile.ZipFile(io.BytesIO(docx)) as z:
                require(z.testzip() is None)
                xml = z.read("word/document.xml")
                ET.fromstring(xml)
                require("测试学生甲" in xml.decode() and "测试学生甲（现名）" not in xml.decode())
                require(b"cantSplit" in xml and b"w:pgSz" in xml)
        check("Word归档快照及OOXML结构有效", verify_docx)
        exported = a.call("批量导出ZIP", "/records/export-batch", "POST", {"ids": [rid]})
        check("ZIP内包含可打开的Word", lambda: require(len(zipfile.ZipFile(io.BytesIO(exported)).namelist()) == 1))
        b.call("批量导出不能越权", "/records/export-batch", "POST", {"ids": [rid]}, 404)
        a.call("批量导出拒绝重复ID", "/records/export-batch", "POST", {"ids": [rid, rid]}, 400)
        headers = "学号,姓名,学院,专业,班级,年级,联系电话\n"
        check("UTF8中文CSV导入成功", lambda: require(a.csv("\ufeff" + headers + "CSV001,导入学生,测试学院,软件工程,测试班,2024级,\n")["count"] == 1))
        check("重复CSV学号触发整体回滚", lambda: a.csv(headers + "CSV002,应回滚学生,,,测试班,,\nTEST001,重复学生,,,测试班,,\n", expected=409))
        check("回滚后未留下前半批数据", lambda: require(a.request("/students?q=CSV002")["total"] == 0))
        check("文件内重复学号拒绝导入", lambda: a.csv(headers + "CSV003,重复1,,,测试班,,\nCSV003,重复2,,,测试班,,\n", expected=400))
        check("错误CSV表头被拒绝", lambda: a.csv("姓名,年龄\n测试,20\n", expected=400))
        check("损坏CSV引号返回明确错误", lambda: a.csv(headers + 'CSV004,"未闭合的字段', expected=400))
        csv_bytes = a.call("导出UTF8学生CSV", "/students/export")
        check("CSV包含BOM和授权学生", lambda: require(csv_bytes.startswith(b"\xef\xbb\xbf") and "测试学生乙" not in csv_bytes.decode("utf-8-sig")))
        admin.call("未知模板变量被拒绝", "/templates", "POST", {"name": "错误模板", "title": "错误", "body": "{studentStatement}{teacherAdvice}{agreement}{secret}", "active": True, "version": 0}, 400)
        admin.call("模板缺少必要栏目被拒绝", "/templates", "POST", {"name": "错误模板", "title": "错误", "body": "{studentStatement}", "active": True, "version": 0}, 400)
        template = admin.call("新增可编辑模板", "/templates", "POST", {"name": "测试模板", "title": "测试记录表", "body": "教师：{teacherName}\n{studentStatement}\n{teacherAdvice}\n{agreement}", "active": True, "version": 0})
        draft = a.call("创建待转交场景草稿", "/records", "POST", dict(payload, templateId=template["id"]))
        sa = admin.call("管理员调整学生负责教师", f"/students/{sa['id']}", "PUT", student_edit(sa, teacherId=users[1]["id"]))
        a.call("转交后原教师不再读取学生档案", f"/students/{sa['id']}", expected=404)
        a.call("转交后原教师保留本人历史记录", f"/records/{rid}")
        b.call("转交档案不开放其他教师旧记录", f"/records/{rid}", expected=404)
        historical = a.call("原作者可继续整理历史草稿", f"/records/generate?recordId={draft['id']}", "POST", dict(payload, templateId=template["id"]))
        check("历史草稿生成仍使用原作者", lambda: require("测试教师A" in historical["content"]))
        historical_admin = admin.call("管理员代整理保留原作者", f"/records/generate?recordId={draft['id']}", "POST", dict(payload, templateId=template["id"]))
        check("管理员不会被错误写为谈话教师", lambda: require("测试教师A" in historical_admin["content"] and "系统管理员" not in historical_admin["content"]))
        long_body = "\n".join(f"第{i+1}段：这是一段虚构的长文本分页测试，用于检查中文段落在Word中是否完整呈现。" * 2 for i in range(55))
        long_record = a.call("保存长文本草稿", f"/records/{draft['id']}", "PUT", dict(payload, templateId=template["id"], content=long_body, version=draft["version"]))
        long_docx = a.call("导出长文本Word", f"/records/{draft['id']}/export")
        with zipfile.ZipFile(io.BytesIO(long_docx)) as z:
            text = z.read("word/document.xml").decode()
            check("长文本尾部完整写入Word", lambda: require("第55段" in text))
        check("记录日期筛选正确", lambda: require(a.request("/records?from=2090-01-01")["total"] == 0))
        a.call("反向日期范围被拒绝", "/records?from=2026-09-08&to=2026-01-01", expected=400)
        admin.call("禁止停用当前管理员", "/users/1", "PUT", {"username": "admin", "displayName": "系统管理员", "role": "ADMIN", "enabled": False}, 400)
        admin.call("管理员重置教师密码", f"/users/{users[1]['id']}/reset-password", "POST", {"password": INITIAL})
        b.call("重置密码后旧会话立即失效", "/auth/me", expected=401)
        b.login("重置密码后能使用初始密码登录", "teacher_b", INITIAL)
        b.call("重置后必须再次改密", "/students", expected=403)
        admin.call("管理员停用教师账号", f"/users/{users[1]['id']}", "PUT", {"username": "teacher_b", "displayName": "测试教师B", "role": "TEACHER", "enabled": False})
        b.call("停用后会话被拒绝", "/auth/me", expected=401)
        check("审计包含查看归档导出与跟进事件", lambda: require(all(admin.request("/audit?q=" + action)["total"] >= 1 for action in ("ARCHIVE_RECORD", "EXPORT_RECORD", "ADD_FOLLOWUP"))))
        cookie = "; ".join(f"{c.name}={c.value}" for c in a.cookies)
        def sample(_):
            start = time.perf_counter()
            request = urllib.request.Request(app.base + "/api/records?size=20", headers={"Cookie": cookie})
            with urllib.request.urlopen(request, timeout=30) as response:
                require(response.status == 200)
                response.read()
            return (time.perf_counter() - start) * 1000
        start = time.perf_counter()
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            values = list(pool.map(sample, range(80)))
        elapsed = time.perf_counter() - start
        ordered = sorted(values)
        evidence["performance"] = {"endpoint": "GET /api/records?size=20", "concurrent_workers": 8, "requests": 80, "records_visible": a.request("/records")["total"], "errors": 0, "mean_ms": round(statistics.mean(values), 2), "p50_ms": round(statistics.median(values), 2), "p95_ms": round(ordered[int(len(values)*.95)-1], 2), "max_ms": round(max(values), 2), "elapsed_seconds": round(elapsed, 3)}
        check("8线程80次列表请求全部成功", lambda: require(len(values) == 80))
        if not app.mysql:
            app.stop()
            backup = app.work / "snapshot.mv.db"
            shutil.copy2(app.work / "runtime/talk.mv.db", backup)
            app.start()
            admin = Client(app.base); admin.login("服务重启后原密码登录", "admin", PASSWORD)
            check("服务重启后归档数据仍在", lambda: require(admin.request(f"/records/{rid}")["content_hash"] == original_hash))
            admin.request("/students", "POST", student_payload("AFTERBACKUP", "恢复时应消失的测试档案", 1))
            app.stop(); shutil.copy2(backup, app.work / "runtime/talk.mv.db"); app.start()
            admin = Client(app.base); admin.login("恢复备份后账号可登录", "admin", PASSWORD)
            check("恢复备份后数据回到备份时点", lambda: require(admin.request("/students?q=AFTERBACKUP")["total"] == 0 and admin.request(f"/records/{rid}")["content_hash"] == original_hash))
        out = ROOT / "test-results"
        out.mkdir(exist_ok=True)
        (out / "export-standard.docx").write_bytes(docx)
        (out / "export-long.docx").write_bytes(long_docx)
    except Exception as error:
        evidence["fatal_error"] = str(error)
        status = 1
    finally:
        evidence["passed"] = sum(test["passed"] for test in RESULTS)
        evidence["failed"] = sum(not test["passed"] for test in RESULTS)
        evidence["complete"] = status == 0
        out = ROOT / "test-results"; out.mkdir(exist_ok=True)
        (out / ("integration-mysql.json" if app.mysql else "integration-h2.json")).write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        if status:
            (out / "failed-application.log").write_bytes((app.work / "application.log").read_bytes())
        app.close()
    raise SystemExit(status)


if __name__ == "__main__":
    main()
