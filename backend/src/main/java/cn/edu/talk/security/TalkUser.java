package cn.edu.talk.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import cn.edu.talk.common.Db;
import cn.edu.talk.common.Time;
import java.util.Map;

public class TalkUser extends User {
    public final long id;
    public final long credentialVersion;
    public final String displayName;
    public final String role;
    public TalkUser(Map<String,Object> row) {
        super(Db.text(row, "username"), Db.text(row, "password_hash"), Db.bool(row,"enabled"), true, true,
            Db.text(row,"locked_until").compareTo(Time.now()) <= 0,
            List.of(new SimpleGrantedAuthority("ROLE_" + Db.text(row,"role"))));
        id = Db.id(row,"id"); credentialVersion = Db.id(row,"credential_version");
        displayName = Db.text(row,"display_name"); role = Db.text(row,"role");
    }
}
