package com.tolox.templateservice.dto;

import com.tolox.templateservice.enums.ChannelType;
import java.util.Map;
import java.util.Set;

public record VariableDiscoveryResponse(
        Map<ChannelType, Set<String>> channelVariables,
        Set<String> allVariables
) {}
