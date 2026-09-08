package cn.edu.talk.common;

import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class Db {
    public final JdbcTemplate jdbc;
    public Db(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String,Object>> list(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = list(sql, args);
        if (rows.isEmpty()) throw new ApiException(404, "数据不存在或无权访问");
        return rows.get(0);
    }
    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
    public long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    public Map<String,Object> page(String select, String countSql, List<Object> args, int page, int size) {
        ApiException.require(page >= 1 && page <= 100000 && size >= 1 && size <= 100, 400, "分页参数不正确");
        long total = count(countSql, args.toArray());
        var withPage = new ArrayList<>(args);
        withPage.add(size); withPage.add((page - 1) * size);
        return Map.of("items", list(select + " LIMIT ? OFFSET ?", withPage.toArray()), "total", total, "page", page, "size", size);
    }
    public static long id(Map<String,Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    public static String text(Map<String,Object> row, String key) { return Objects.toString(row.get(key), ""); }
    public static boolean bool(Map<String,Object> row, String key) {
        Object value = row.get(key);
        return Boolean.TRUE.equals(value) || (value instanceof Number n && n.intValue() != 0);
    }
}
