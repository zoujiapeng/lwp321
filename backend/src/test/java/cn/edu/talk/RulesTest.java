package cn.edu.talk;

import cn.edu.talk.common.*;
import cn.edu.talk.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    private static final String TEMPLATE="{studentStatement}\n{teacherAdvice}\n{agreement}";
    @Test void literalReplacementDoesNotEvaluateExpressions() {
        var text=TemplateEngine.render(TEMPLATE,Map.of("studentStatement","${7*7} {teacherAdvice}","teacherAdvice","建议","agreement","约定"));
        assertEquals("${7*7} {teacherAdvice}\n建议\n约定",text);
    }
    @Test void dollarAndBackslashArePreserved() {
        assertEquals("$100\\folder\n建议\n约定",TemplateEngine.render(TEMPLATE,Map.of("studentStatement","$100\\folder","teacherAdvice","建议","agreement","约定")));
    }
    @Test void unknownVariableRejected() { assertThrows(ApiException.class,()->TemplateEngine.validate(TEMPLATE+"{password}")); }
    @Test void requiredSectionsCannotBeOmitted() { assertThrows(ApiException.class,()->TemplateEngine.validate("{studentStatement}")); }
    @Test void missingFactsAreNotInvented() { assertEquals("未填写\n未填写\n未填写",TemplateEngine.render(TEMPLATE,Map.of())); }
    @Test void passwordMustBeLongEnough() { assertThrows(ApiException.class,()->Inputs.password("abc123")); }
    @Test void passwordRequiresLettersAndNumbers() { assertThrows(ApiException.class,()->Inputs.password("abcdefghijklmnop")); }
    @Test void validPasswordAccepted() { assertDoesNotThrow(()->Inputs.password("ExampleOnly123!")); }
    @Test void csvFormulasAreNeutralized() { assertEquals("'=1+1",StudentService.csvSafe("=1+1")); assertEquals("'+SUM(A1)",StudentService.csvSafe("+SUM(A1)")); }
    @Test void normalCsvTextUnchanged() { assertEquals("测试学生",StudentService.csvSafe("测试学生")); }
    @Test void hashStableAndSensitiveToChanges() { assertEquals(RecordService.hash("正文"),RecordService.hash("正文")); assertNotEquals(RecordService.hash("正文"),RecordService.hash("正文2")); assertEquals(64,RecordService.hash("正文").length()); }
    @Test void invalidDateRejected() { assertThrows(ApiException.class,()->Time.date("2026-02-30")); }
}
