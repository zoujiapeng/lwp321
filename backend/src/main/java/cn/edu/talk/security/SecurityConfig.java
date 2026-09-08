package cn.edu.talk.security;

import cn.edu.talk.common.*;
import cn.edu.talk.service.AuditService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService users(Db db) {
        return username -> {
            var rows=db.list("SELECT * FROM app_user WHERE username=?",username);
            if(rows.isEmpty()) throw new UsernameNotFoundException("账号或密码错误");
            return new TalkUser(rows.get(0));
        };
    }
    @Bean SecurityFilterChain chain(HttpSecurity http, Db db, AuditService audit) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/csrf","/api/health").permitAll()
            .requestMatchers("/api/**").authenticated().anyRequest().permitAll());
        // Keep Spring Security's session-backed CSRF protection. The Vue client obtains a token before POST/PUT/DELETE.
        http.formLogin(form -> form.loginProcessingUrl("/api/auth/login").permitAll()
            .successHandler((request,response,auth) -> {
                Actor actor=Actor.from(auth);
                db.jdbc.update("UPDATE app_user SET failed_attempts=0,locked_until=NULL WHERE id=?",actor.id());
                audit.log(actor,"LOGIN","USER",actor.id(),"");
                AccountFilter.json(response,200,"登录成功");
            })
            .failureHandler((request,response,ex) -> {
                String name=request.getParameter("username");
                if(name!=null && name.length()<=40) {
                    db.jdbc.update("UPDATE app_user SET failed_attempts=failed_attempts+1 WHERE username=?",name);
                    String until=LocalDateTime.now(Time.ZONE).plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                    db.jdbc.update("UPDATE app_user SET locked_until=? WHERE username=? AND failed_attempts>=5",until,name);
                }
                audit.log(null,"LOGIN_FAILED","USER",null,"");
                AccountFilter.json(response,401,"账号或密码错误，或账号已停用/暂时锁定");
            }));
        http.logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
            .logoutSuccessHandler((request,response,auth) -> AccountFilter.json(response,200,"已退出登录")));
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((request,response,ex) -> AccountFilter.json(response,401,"请先登录"))
            .accessDeniedHandler((request,response,ex) -> AccountFilter.json(response,403,"权限不足或安全令牌已过期，请刷新页面")));
        http.addFilterBefore(new AccountFilter(db),UsernamePasswordAuthenticationFilter.class);
        http.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")));
        return http.build();
    }
}
