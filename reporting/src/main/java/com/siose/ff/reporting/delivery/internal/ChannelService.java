package com.siose.ff.reporting.delivery.internal;

import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.reporting.delivery.api.ChannelPreferences;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The signed-in tenant's channels. An enabled channel without a usable address is refused. */
@Service
public class ChannelService {

    private final ChannelStore store;

    ChannelService(ChannelStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public ChannelPreferences get() {
        return store.preference(TenantContext.requireTenantId());
    }

    @Transactional
    public ChannelPreferences save(ChannelPreferences request) {
        ChannelPreferences stored = new ChannelPreferences(
                request.emailEnabled(),
                request.emailEnabled() ? address(request.emailAddress()) : null,
                request.webhookEnabled(),
                request.webhookEnabled() ? webhook(request.webhookUrl()) : null);
        String tenantId = TenantContext.requireTenantId();
        store.save(tenantId, stored);
        return stored;
    }

    private static String address(String email) {
        if (email == null || email.isBlank() || email.length() > 320 || email.chars().anyMatch(Character::isWhitespace)
                || email.indexOf('@') < 1 || email.indexOf('@') == email.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An enabled email channel needs an address");
        }
        return email;
    }

    private static String webhook(String url) {
        if (url == null || url.length() > 500 || url.chars().anyMatch(Character::isWhitespace)
                || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An enabled webhook needs an http(s) address");
        }
        return url;
    }
}
