package cn.edu.talk.common;

import jakarta.validation.constraints.*;
import java.util.List;

public final class Inputs {
    private Inputs() {}
    public record StudentInput(@NotBlank @Size(max=40) String studentNo,
        @NotBlank @Size(max=60) String name, @Size(max=100) String college,
        @Size(max=100) String major, @NotBlank @Size(max=80) String className,
        @Size(max=20) String grade, @Size(max=30) String phone,
        Long teacherId, boolean active, long version) {}
    public record UserInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,40}") String username,
        @NotBlank @Size(max=60) String displayName, @NotBlank String role,
        String password, boolean enabled) {}
    public record PasswordInput(@NotBlank String oldPassword, @NotBlank String newPassword) {}
    public record ResetInput(@NotBlank String password) {}
    public record TalkInput(@NotNull Long studentId, @NotNull Long templateId,
        @NotBlank @Size(max=120) String topic, @NotBlank String category,
        @NotBlank String occurredAt, @Min(1) @Max(480) int durationMinutes,
        @NotBlank @Size(max=120) String place, @NotBlank String mode,
        @Size(max=2000) String background, @Size(max=4000) String studentStatement,
        @Size(max=4000) String teacherAdvice, @Size(max=4000) String agreement,
        String followupDate, @Size(max=20000) String content, long version) {}
    public record ArchiveInput(long version, boolean confirmed) {}
    public record DeleteInput(long version) {}
    public record FollowupInput(@NotBlank @Size(max=4000) String content, String nextDate,
        boolean resolved, long version) {}
    public record TemplateInput(@NotBlank @Size(max=80) String name,
        @NotBlank @Size(max=120) String title, @NotBlank @Size(max=10000) String body,
        boolean active, long version) {}
    public record BatchInput(@NotEmpty @Size(max=50) List<@NotNull Long> ids) {}
    public static String clean(String value) {
        if(value==null) return "";
        validText(value); return value.strip();
    }
    public static void validText(String value) {
        ApiException.require(value.codePoints().allMatch(c -> c==9 || c==10 || c==13 || (c>=32 && c<=0xD7FF) || (c>=0xE000 && c<=0xFFFD) || (c>=0x10000 && c<=0x10FFFF)),400,"输入包含不能写入Word的控制字符，请清理后重试");
    }
    public static void password(String password) {
        ApiException.require(password != null && password.length() >= 12 && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72,
            400, "密码至少12个字符且不超过72字节");
        ApiException.require(password.matches(".*[A-Za-z].*") && password.matches(".*[0-9].*"), 400, "密码必须同时包含字母和数字");
    }
}
