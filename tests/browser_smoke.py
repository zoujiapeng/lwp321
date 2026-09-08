#!/usr/bin/env python3
"""Real browser checks against this application's disposable localhost test server.
All fixtures are fictional. Requires Playwright and its Chromium browser.
"""
import datetime as dt
import importlib.metadata
import json
import os
import re
from pathlib import Path
import zipfile
from playwright.sync_api import sync_playwright, expect
from integration import Application, Client, INITIAL, PASSWORD, student_payload, require

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'test-results' / 'browser'
OUT.mkdir(parents=True, exist_ok=True)
results, errors = [], []


def checked(name, passed=True):
    require(passed, name)
    results.append({'name': name, 'passed': True})
    print('PASS', name, flush=True)


def main():
    app = Application()
    version = ''
    success = False
    try:
        app.start()
        client = Client(app.base)
        client.login('界面测试初始化账号', 'admin', INITIAL)
        client.change()
        client.login('界面测试初始化新密码', 'admin', PASSWORD)
        client.request('/users', 'POST', {'username': 'teacher_example', 'displayName': '示例教师', 'role': 'TEACHER', 'password': INITIAL, 'enabled': True})
        students = [client.request('/students', 'POST', student_payload(f'EXAMPLE2024{i+1:03d}', '示例学生' + letter, 1)) for i, letter in enumerate('甲乙丙丁戊己庚辛')]
        now = dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
        categories = ['学业发展', '生活适应', '人际沟通', '就业规划', '其他']
        themes = ['课程任务与学习计划', '新学期生活安排', '小组任务分工沟通', '实习准备与求职计划', '日常学习近况交流']
        for i in range(8):
            occurred = (now - dt.timedelta(days=2+i*13)).replace(hour=10, minute=0, second=0, microsecond=0).isoformat()[:19]
            data = {'studentId': students[i]['id'], 'templateId': 1, 'topic': themes[i % 5], 'category': categories[i % 5], 'occurredAt': occurred,
                'durationMinutes': 30, 'place': '示例办公室', 'mode': '面谈', 'background': '本条为虚构测试数据，用于验证系统流程。',
                'studentStatement': '学生介绍了近期学习安排，希望将课程任务分解到每周，按计划推进。',
                'teacherAdvice': '建议列出近期需要完成的任务，记录截止日期，并根据实际情况安排学习时间。',
                'agreement': '学生整理一份任务清单，教师按约定日期查看落实情况。',
                'followupDate': (now.date() + dt.timedelta(days=i-2)).isoformat(), 'content': '', 'version': 0}
            data['content'] = client.request('/records/generate', 'POST', data)['content']
            record = client.request('/records', 'POST', data)
            if i < 6:
                client.request(f"/records/{record['id']}/archive", 'POST', {'version': record['version'], 'confirmed': True})
        with sync_playwright() as pw:
            browser = pw.chromium.launch(headless=True, executable_path=os.environ.get('CHROMIUM_PATH'))
            version = browser.version
            context = browser.new_context(viewport={'width': 1440, 'height': 1000}, locale='zh-CN', timezone_id='Asia/Shanghai', accept_downloads=True)
            page = context.new_page()
            page.on('pageerror', lambda error: errors.append(str(error)))
            page.goto(app.base, wait_until='networkidle')
            page.locator('#username').wait_for()
            page.screenshot(path=str(OUT / '01-login.png'))
            page.locator('#username').fill('admin')
            page.locator('#password').fill(PASSWORD)
            page.get_by_role('button', name='登录', exact=True).click()
            page.get_by_role('heading', name='工作台', exact=True).wait_for()
            page.wait_for_load_state('networkidle')
            checked('真实浏览器登录进入工作台')
            page.screenshot(path=str(OUT / '02-dashboard.png'))
            page.goto(app.base + '/#/students', wait_until='networkidle')
            page.get_by_role('button', name='新增学生', exact=True).click()
            for selector, value in {'#student-no':'BROWSER2026001', '#student-name':'界面测试学生', '#student-college':'示例学院', '#student-major':'计算机科学与技术', '#student-class':'计算机2401班', '#student-grade':'2024级'}.items():
                page.locator(selector).fill(value)
            page.get_by_role('button', name='保存档案', exact=True).click()
            page.locator('dialog[open]').wait_for(state='hidden')
            page.get_by_text('界面测试学生', exact=True).wait_for()
            checked('界面新增学生并显示在列表')
            page.screenshot(path=str(OUT / '03-students.png'))
            sid = client.request('/students?q=BROWSER2026001')['items'][0]['id']
            page.goto(app.base + f'/#/records/new?studentId={sid}', wait_until='networkidle')
            values = {'#talk-topic':'阶段性学习计划沟通', '#talk-time':(now-dt.timedelta(days=1)).strftime('%Y-%m-%dT10:00'), '#talk-place':'示例辅导员办公室',
                '#talk-background':'本条为虚构的界面验收记录，检查实际填写、生成、归档与跟进流程。',
                '#talk-statement':'学生表示近期课程任务较多，希望合理安排时间，减少临近截止日期才集中完成任务的情况。',
                '#talk-advice':'教师建议将任务按截止日期排序，优先完成本周任务，每天预留固定学习时间，并及时反馈难以完成的部分。',
                '#talk-agreement':'学生将在本周五前整理任务清单，教师下周联系学生了解执行情况。', '#talk-followup':(now.date()+dt.timedelta(days=7)).isoformat()}
            for selector, value in values.items():
                page.locator(selector).fill(value)
            page.get_by_role('button', name='根据要点生成正文', exact=True).click()
            expect(page.locator('#talk-content')).to_have_value(re.compile('学生表示近期课程任务较多'))
            checked('界面生成正文并保留事实')
            page.screenshot(path=str(OUT / '04-editor.png'), full_page=True)
            page.get_by_role('button', name='保存草稿并查看', exact=True).click()
            page.get_by_role('heading', name='谈话记录详情', exact=True).wait_for()
            checked('草稿保存后进入详情')
            page.get_by_role('button', name='核对并归档', exact=True).click()
            checked('未勾选人工核对时不能归档', not page.get_by_role('button', name='确认归档', exact=True).is_enabled())
            page.locator('dialog[open] input[type=checkbox]').check()
            page.get_by_role('button', name='确认归档', exact=True).click()
            page.get_by_role('button', name='追加跟进', exact=True).wait_for()
            checked('人工确认后归档且编辑按钮消失', page.get_by_role('link', name='编辑草稿', exact=True).count() == 0)
            page.screenshot(path=str(OUT / '05-archived-record.png'), full_page=True)
            record_url = page.url
            with page.expect_download() as transfer:
                page.get_by_role('button', name='导出Word', exact=True).click()
            target = OUT / 'browser-export.docx'
            transfer.value.save_as(target)
            with zipfile.ZipFile(target) as archive:
                require('阶段性学习计划沟通' in archive.read('word/document.xml').decode())
            checked('浏览器真实下载Word可解包且正文完整')
            page.get_by_role('button', name='追加跟进', exact=True).click()
            message = '本条为虚构跟进数据。学生已提交任务清单，教师核对后确认本轮约定事项已完成。'
            page.locator('#followup-content').fill(message)
            page.get_by_role('button', name='保存跟进', exact=True).click()
            page.get_by_text(message, exact=True).wait_for()
            checked('界面追加跟进并显示时间线')
            page.screenshot(path=str(OUT / '06-followup.png'))
            page.get_by_role('button', name='记录正文', exact=True).click()
            page.emulate_media(media='print')
            page.pdf(path=str(OUT / 'browser-print.pdf'), format='A4', print_background=True, prefer_css_page_size=True)
            page.emulate_media(media='screen')
            checked('浏览器打印生成A4 PDF')
            for n, path, heading, name in [(7,'/records','谈话记录','records'), (8,'/templates','记录模板','templates'), (9,'/users','账号管理','users'), (10,'/audit','操作日志','audit')]:
                page.goto(app.base + '/#' + path, wait_until='networkidle')
                page.get_by_role('heading', name=heading, exact=True).wait_for()
                page.screenshot(path=str(OUT / f'{n:02d}-{name}.png'))
                checked(heading + '页面实际加载')
            page.goto(app.base + '/#/records', wait_until='networkidle')
            page.locator('thead input[type=checkbox]').check()
            with page.expect_download() as transfer:
                page.get_by_role('button', name='批量导出Word', exact=True).click()
            transfer.value.save_as(OUT / 'browser-batch.zip')
            checked('界面批量导出9条Word文件', len(zipfile.ZipFile(OUT/'browser-batch.zip').namelist()) == 9)
            page.goto(app.base + '/#/records/new', wait_until='networkidle')
            page.locator('#talk-topic').fill('未保存的测试内容')
            dialogs = []
            def accept_dialog(dialog):
                dialogs.append(dialog.message)
                dialog.accept()
            page.once('dialog', accept_dialog)
            page.get_by_role('link', name='返回列表', exact=True).click()
            page.get_by_role('heading', name='谈话记录', exact=True).wait_for()
            checked('未保存离开时给出确认提示', bool(dialogs))
            page.set_viewport_size({'width': 390, 'height': 844})
            for n, path, heading, name in [(11,'/','工作台','mobile-dashboard'), (12,'/students','学生档案','mobile-students')]:
                page.goto(app.base + '/#' + path, wait_until='networkidle')
                page.get_by_role('heading', name=heading, exact=True).wait_for()
                checked('390px手机宽度' + heading + '无整页横向溢出', page.evaluate('() => document.documentElement.scrollWidth <= window.innerWidth + 1'))
                page.screenshot(path=str(OUT / f'{n:02d}-{name}.png'), full_page=True)
            page.set_viewport_size({'width': 1440, 'height': 1000})
            page.goto(record_url, wait_until='networkidle')
            checked('刷新后归档记录和跟进仍可读取', '阶段性学习计划沟通' in page.locator('main').inner_text())
            checked('全过程无未捕获JavaScript异常', not errors)
            browser.close()
            success = True
    except Exception as error:
        results.append({'name': '浏览器流程异常', 'passed': False, 'error': str(error)})
        print('FAIL', str(error), flush=True)
    finally:
        app.close()
        evidence = {'complete': success, 'browser': 'Chromium ' + version, 'playwright_version': importlib.metadata.version('playwright'), 'executed_at_utc': dt.datetime.now(dt.timezone.utc).isoformat(), 'tests': results, 'javascript_errors': errors, 'fixtures': '全部为虚构验收数据，测试使用独立临时数据库'}
        (OUT / 'browser-results.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2))
    raise SystemExit(0 if success else 1)

if __name__ == '__main__':
    main()
