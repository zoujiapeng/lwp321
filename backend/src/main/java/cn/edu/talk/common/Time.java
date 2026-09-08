package cn.edu.talk.common;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class Time {
    private Time() {}
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static String now() { return LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")); }
    public static String today() { return LocalDate.now(ZONE).toString(); }
    public static String date(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value).toString(); }
        catch (Exception ex) { throw new ApiException(400, "日期格式应为 yyyy-MM-dd"); }
    }
    public static String occurred(String value) {
        try {
            var date = LocalDateTime.parse(value);
            ApiException.require(!date.isAfter(LocalDateTime.now(ZONE).plusMinutes(1)), 400, "谈话发生时间不能晚于当前时间");
            ApiException.require(date.getYear() >= 2000, 400, "谈话日期不合理");
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) { throw new ApiException(400, "请填写有效的谈话日期和时间"); }
    }
}
