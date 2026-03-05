package com.tolox.templateservice.dto;

import com.tolox.templateservice.enums.ChannelType;
import java.util.Map;

public record RenderRequest(
        String applicationCode,
        String templateCode,
        ChannelType channel,
        String locale,
        Map<String, Object> data
) {}
