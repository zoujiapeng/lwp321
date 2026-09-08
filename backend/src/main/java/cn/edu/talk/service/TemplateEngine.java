package cn.edu.talk.service;

import cn.edu.talk.common.ApiException;
import java.util.*;
import java.util.regex.*;

/** Literal substitution only: no expression evaluation, scripts, network requests or invented facts. */
public final class TemplateEngine {
    private TemplateEngine() {}
    public static final Set<String> VARIABLES=Set.of("studentName","studentNo","className","teacherName","topic","occurredAt","place","mode","durationMinutes","background","studentStatement","teacherAdvice","agreement","followupDate");
    private static final Pattern PLACEHOLDER=Pattern.compile("\\{([^{}]+)\\}");
    public static void validate(String body) {
        Matcher matcher=PLACEHOLDER.matcher(body);
        while(matcher.find()) ApiException.require(VARIABLES.contains(matcher.group(1)),400,"不支持的模板变量："+matcher.group(1));
        for(String required:List.of("studentStatement","teacherAdvice","agreement"))
            ApiException.require(body.contains("{"+required+"}"),400,"模板必须保留学生陈述、教师建议和双方约定三个变量");
    }
    public static String render(String body,Map<String,String> values) {
        validate(body); Matcher matcher=PLACEHOLDER.matcher(body); StringBuffer output=new StringBuffer();
        while(matcher.find()) {
            String value=values.getOrDefault(matcher.group(1),"");
            matcher.appendReplacement(output,Matcher.quoteReplacement(value.isBlank()?"未填写":value));
        }
        matcher.appendTail(output); return output.toString();
    }
}
