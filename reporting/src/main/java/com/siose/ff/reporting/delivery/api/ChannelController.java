package com.siose.ff.reporting.delivery.api;

import com.siose.ff.reporting.delivery.internal.ChannelService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/channels")
@PreAuthorize("hasAnyRole('LOGISTICS_PLANNER', 'REPORT_VIEWER', 'TENANT_ADMIN')")
public class ChannelController {

    private final ChannelService channels;

    public ChannelController(ChannelService channels) {
        this.channels = channels;
    }

    @GetMapping
    public ChannelPreferences get() {
        return channels.get();
    }

    @PutMapping
    public ChannelPreferences put(@RequestBody ChannelPreferences request) {
        return channels.save(request);
    }
}
