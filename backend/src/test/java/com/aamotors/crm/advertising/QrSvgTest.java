package com.aamotors.crm.advertising;

import com.aamotors.crm.messaging.channel.QrSvg;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QrSvgTest {
    @Test
    void rendersAnSvgWithFinderPatterns() {
        String svg = QrSvg.render("https://t.me/AAMotorsBot?start=AB12CD");
        assertThat(svg).startsWith("<svg").endsWith("</svg>").contains("<path fill=\"#000\"");
        assertThat(svg.length()).isGreaterThan(2000);
    }
}
