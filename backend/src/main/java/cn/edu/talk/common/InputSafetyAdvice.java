package cn.edu.talk.common;

import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Reject XML-incompatible characters before persisting text that may be exported to Word. */
@ControllerAdvice
public class InputSafetyAdvice extends RequestBodyAdviceAdapter {
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return parameter.getParameterType().isRecord();
    }
    @Override public Object afterBodyRead(Object body, HttpInputMessage message, MethodParameter parameter,
        Type type, Class<? extends HttpMessageConverter<?>> converter) {
        try {
            for(var component:body.getClass().getRecordComponents()) {
                Object value=component.getAccessor().invoke(body);
                if(value instanceof String text) Inputs.validText(text);
            }
            return body;
        } catch(ReflectiveOperationException ex) { throw new IllegalStateException("输入校验失败",ex); }
    }
}
