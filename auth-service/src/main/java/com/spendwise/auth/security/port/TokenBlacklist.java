package com.spendwise.auth.security.port;

import java.time.Duration;

public interface TokenBlacklist {

    void addToBlacklist(String jti, Duration ttl);

    boolean isBlacklisted(String jti);
}
