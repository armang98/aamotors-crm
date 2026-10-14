package com.aamotors.crm.campaign;

import com.aamotors.crm.messaging.channel.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.campaigns")
public record CampaignProperties(int batchSize, long costWhatsappAmd, long costViberBusinessAmd, long costTelegramAmd, long costViberBotAmd) {

    public long costOf(Channel ch) {
        if (ch == null) return 0;
        return switch (ch) {
            case WHATSAPP -> costWhatsappAmd;
            case VIBER_BUSINESS -> costViberBusinessAmd;
            case TELEGRAM -> costTelegramAmd;
            case VIBER_BOT -> costViberBotAmd;
        };
    }

    public boolean hasAnyCost() {
        return costWhatsappAmd > 0 || costViberBusinessAmd > 0 || costTelegramAmd > 0 || costViberBotAmd > 0;
    }
}
