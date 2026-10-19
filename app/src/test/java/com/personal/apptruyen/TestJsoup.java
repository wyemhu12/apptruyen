package com.personal.apptruyen;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestJsoup {
    @Test
    void parseStaticHtmlExtractsStoryLinks() {
        String html = "<html><body>"
                + "<a href=\"/truyen?genre=tien-hiep\">Tiên Hiệp</a>"
                + "<div class=\"story-cover\"><img src=\"https://tangthuvien.org/cover.jpg\" alt=\"cover\"/></div>"
                + "</body></html>";
        Document doc = Jsoup.parse(html, "https://tangthuvien.org");
        var links = doc.select("a[href*=?genre=]");
        Assertions.assertEquals(1, links.size());
        Assertions.assertEquals("Tiên Hiệp", links.first().text());
        var coverImg = doc.selectFirst(".story-cover img");
        Assertions.assertEquals("https://tangthuvien.org/cover.jpg", coverImg.attr("src"));
    }
}
