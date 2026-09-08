# 师生谈心谈话记录生成系统

基于 Spring Boot 4 + Vue 3 的可部署本科毕业设计。面向教师和管理员，完成 **学生建档 → 录入实际谈话要点 → 模板生成草稿 → 人工核对归档 → Word导出 → 跟进反馈**。

本项目不是静态页面集合。前端操作调用真实后端，数据写入关系型数据库；归档、权限、导出与恢复具有自动化测试。生成方式是可解释的模板变量替换，不调用外部大模型，不会补写未发生的谈话。

## 1. 快速运行（Linux）

运行包已经包含 `dist/talk-records.jar`。只需 Java 17 或更高版本、Bash、OpenSSL 和 curl，无须另装 Node.js 或 MySQL。

```bash
unzip talk-records-linux.zip
cd talk-records
bash scripts/start.sh
```

浏览器打开 `http://127.0.0.1:8080`。初始账号 `admin`。首次启动脚本为本次安装生成独立随机密码，保存在仅当前用户可读的 `.env.local`：

```bash
grep APP_ADMIN_PASSWORD .env.local
```

首次登录必须修改密码。**没有通用默认密码，没有预置真实学生信息。** 请不要公开 `.env.local`、数据库或备份文件。

停止服务：`bash scripts/stop.sh`。运行日志：`runtime/app.log`。默认数据库：`runtime/talk.mv.db`，退出和重启后数据保留。

## 2. 从源码构建

准备 Java 17+、Maven 3.6.3+、Node.js 22.12+（含npm），首次下载依赖需网络。

```bash
bash scripts/build.sh
bash scripts/start.sh
```

构建执行 Vue 编译和后端单元测试，将前端静态文件打入 JAR。业务界面无需另启前端服务。开发时也可分别运行后端与 `cd frontend && npm run dev`；Vite通过代理访问8080端口。

## 3. 使用顺序

管理员首次改密后，进入“账号管理”创建教师账号，并单独告知初始密码；再在“学生档案”新增学生或下载CSV模板批量导入，将学生分配给负责教师。教师也可以建立本人负责的学生档案。

进入“新建谈话记录”，填写学生、主题、实际时间、地点、时长和原始要点。填写学生陈述、教师建议、双方约定后可点击生成；正文可继续修改。缺少要点时允许暂存草稿，但不能生成虚构事实或直接归档。

在详情页对照原始要点和正文，勾选确认后归档。归档保存学生信息、教师名称、模板标题和正文快照，禁止直接改写或删除。Word文件提供待实际签署的签字栏，系统不代签。

按计划跟进日期在工作台查看待办，记录实际落实情况。后续跟进以独立条目追加；完成后待办消失，原归档正文不变。

## 4. 权限与边界

| 对象 | 教师 | 管理员 |
|---|---|---|
| 学生档案 | 本人负责的档案 | 全部，可调整负责教师 |
| 谈话记录 | 本人创建的记录 | 全部 |
| 已归档正文 | 不可改写或删除 | 同样不可改写或删除 |
| 跟进、导出 | 权限范围内 | 权限范围内 |
| 账号、模板、日志 | 不可管理 | 可管理 |

转交学生档案不会自动开放前任教师的全部历史记录。旧作者保留本人历史记录；管理员可以统一查询。此规则用于避免一次人员调整就批量泄露谈话资料。学校采用其他交接制度时，需要明确授权后再修改服务端规则。

当前提供两种账号角色，**没有学生登录端**。学生是谈话对象，不是必须注册系统的操作者。没有短信、录音识别、心理诊断、自动风险标签或付费AI接口。

## 5. 数据导入与导出

学生导入：UTF-8 CSV，表头为 `学号,姓名,学院,专业,班级,年级,联系电话`，一次最多500条且文件不超过2MB。所有行校验通过才提交，重复或错误会整批回滚，不覆盖已有档案。示例文件 `examples/students-fictional.csv` 全部为虚构数据，不会自动导入。

记录导出：单份DOCX或最多50条的ZIP；草稿导出带有草稿标识。归档导出使用保存的快照，不随当前学生姓名或模板变化。长正文使用段落自然分页，基本信息表的单行禁止跨页拆分。页面也提供浏览器打印/保存PDF。

CSV导出附带UTF-8 BOM并对公式型文本作转义。用WPS输入带前导零的学号时请按文本填写，避免表格软件先改写学号。

## 6. 备份与恢复

默认H2适合单机小规模使用，备份前必须正常停止服务：

```bash
bash scripts/stop.sh
bash scripts/backup.sh
bash scripts/start.sh
```

恢复时先停机，再运行 `bash scripts/restore-h2.sh backups/h2-具体日期.mv.db`，按提示输入 `RESTORE`。脚本会先保留恢复前数据库副本。请把备份另外保存到受控位置，并定期实际验证恢复，不能只保存在同一块磁盘。

`backup.sh` 也提供MySQL的mysqldump备份分支，需在 `.env.local` 配置 MYSQL_HOST、MYSQL_PORT、MYSQL_DATABASE、DB_USER、DB_PASSWORD。此脚本不提供网络传输或异地备份账户。

## 7. MySQL与部署

数据库连接由 `DB_URL`、`DB_USER`、`DB_PASSWORD` 指定；不设置时使用H2文件。MySQL数据库须预先建立，使用UTF-8字符集；账号需要本库建表、建索引与常规读写权限，首次启动按 `schema.sql` 初始化。

```bash
DB_URL='jdbc:mysql://127.0.0.1:3306/talk_records?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
DB_USER=talk
DB_PASSWORD='请替换为独立强密码'
```

以上值写入 `.env.local` 并设置600权限后重新启动。H2和MySQL是两个独立数据源，切换连接**不会自动迁移既有数据**。

提供 `Dockerfile`、`compose.yml`、systemd单元和Nginx反向代理示例。Compose使用前在 `.env` 设置 DB_PASSWORD、MYSQL_ROOT_PASSWORD、APP_ADMIN_PASSWORD，然后 `docker compose up -d --build`。这些示例不包含域名或TLS证书；对外开放前必须配置HTTPS、访问范围、备份责任与 `COOKIE_SECURE=true`。默认仅监听本机回环地址。不要直接把测试环境设置称为正式生产加固。

## 8. 代码结构与修改入口

```text
backend/src/main/java/cn/edu/talk/
  common/      参数、异常、时间、数据库访问
  security/    登录、角色、会话、密码与安全过滤
  service/     学生、模板、谈话、导出及审计规则
  web/         HTTP接口
frontend/src/
  views/       每个业务页面一个Vue文件
  components/  弹窗、分页
  api.js       fetch请求、安全令牌和下载
  router.js    简单哈希路由与页面权限
  style.css    集中维护的普通CSS
scripts/       构建、启动、停止、备份和恢复
 tests/        接口与真实浏览器验收（使用临时数据）
```

改颜色、间距和布局优先编辑 `frontend/src/style.css`；改表单编辑对应 `views/*.vue`；改生成栏目优先用系统内“记录模板”，不必改代码。模板变量只有字面替换，不允许执行Java、JavaScript或表达式。

## 9. 可复现验证

```bash
cd backend && mvn verify && cd ..
python3 tests/integration.py
python3 -m pip install playwright
python3 -m playwright install chromium
python3 tests/browser_smoke.py
```

接口测试使用临时H2文件，自动清理；MySQL测试必须显式使用包含 `talk_test` 的可丢弃测试库，严禁指向真实业务库。GitHub Actions会同时验证H2、MySQL及浏览器流程，生成原始JSON、DOCX、截图和报告。请查看本仓库最近一次工作流的实际结果，不要把尚未完成或失败的工作流描述为通过。

现有单元测试12项，H2业务/接口检查99项，MySQL检查95项；MySQL少4项是因为H2文件复制和恢复步骤不适用于MySQL。浏览器测试另行记录结果。8线程80次请求仅是小样本响应检查，不代表大规模并发容量。

## 10. 实际使用注意

- 密码使用BCrypt单向哈希，不是明文或可解密存储。修改密码、重置密码、停用或变更角色会使旧会话失效。
- 服务端启用CSRF保护和逐对象权限检查；不能只依赖前端隐藏按钮。
- 数据库中的谈话正文没有应用层加密；服务器文件、磁盘、备份和管理员访问权限仍须单独管理。
- SHA-256仅用于校验正文一致性，不等同于电子签名或不可篡改存证。
- 当前没有校内真实试用、正式安全审计或所有版本WPS的兼容性认证。交付文件采用标准OOXML，并提供PDF参考版；使用者修改格式、安装字体或编辑内容后应再次检查分页。
- 本系统与配套文档使用了AI辅助。毕业论文签名、教师意见、身份信息和真实实施过程由本人及指导教师核对填写，不应复制模板中其他人的身份信息。
