package com.aamotors.crm.messaging.channel;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Looks up the provider bean for each channel, in fallback order. */
@Component
public class ChannelRegistry {

    private final Map<Channel, ChannelProvider> providers = new EnumMap<>(Channel.class);

    public ChannelRegistry(List<ChannelProvider> all) {
        for (ChannelProvider p : all) {
            if (providers.putIfAbsent(p.channel(), p) != null) {
                throw new IllegalStateException("Two providers registered for channel " + p.channel());
            }
        }
        for (Channel c : Channel.values()) {
            if (!providers.containsKey(c)) throw new IllegalStateException("No provider for channel " + c);
        }
    }

    public ChannelProvider get(Channel channel) { return providers.get(channel); }

    public List<ChannelProvider> inFallbackOrder() {
        return Channel.FALLBACK_ORDER.stream().map(providers::get).toList();
    }
}
