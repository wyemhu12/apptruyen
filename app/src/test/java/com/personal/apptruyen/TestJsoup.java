import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class TestJsoup {
    public static void main(String[] args) throws Exception {
        Document doc = Jsoup.connect("https://tangthuvien.org/truyen").get();
        var links = doc.select("a[href*=?genre=]");
        System.out.println("Links found: " + links.size());
        if (links.size() > 0) {
            System.out.println("First link: " + links.first().attr("href") + " -> " + links.first().text());
        }
        
        var coverImg = doc.selectFirst(".story-cover img, .book-info-pic img");
        System.out.println("Cover img selector '.story-cover img, .book-info-pic img': " + (coverImg != null));
        
        // Let's get a story page and find its cover image
        Document storyDoc = Jsoup.connect("https://tangthuvien.org/doc-quyen-dich-toan-chuc-phap-su-he-thong-hop-mu-khong-dung-dan").get();
        var img = storyDoc.selectFirst("img[alt=cover], .image-cover");
        System.out.println("Cover img on story page (alt=cover): " + (img != null ? img.attr("src") : "null"));
    }
}
