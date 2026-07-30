package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsSource;
import com.ksh.features.discovery.entity.NewsSourceType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class RssNewsSourceAdapter implements NewsSourceAdapter {

    private static final int MAX_ITEMS = 40;

    private final NewsHttpClient httpClient;

    public RssNewsSourceAdapter(NewsHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public NewsSourceType supportedType() {
        return NewsSourceType.RSS;
    }

    @Override
    public List<NewsCandidate> fetch(NewsSource source) {
        String xml = httpClient.get(
                source.getFeedUrl(),
                MediaType.parseMediaType("application/rss+xml")
        );
        return parse(xml, source);
    }

    List<NewsCandidate> parse(String xml, NewsSource source) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            NodeList items = document.getElementsByTagName("item");
            List<NewsCandidate> result = new ArrayList<>();
            for (int index = 0; index < Math.min(items.getLength(), MAX_ITEMS); index++) {
                Element item = (Element) items.item(index);
                String title = childText(item, "title");
                String link = childText(item, "link");
                if (title == null || title.isBlank() || link == null || link.isBlank()) {
                    continue;
                }
                result.add(new NewsCandidate(
                        childText(item, "guid"),
                        NewsTextSupport.plainText(title, 700),
                        NewsTextSupport.plainText(childText(item, "description"), 480),
                        link.trim(),
                        enclosureUrl(item),
                        source.getLanguageCode(),
                        source.getDefaultCategory(),
                        NewsTextSupport.parseFeedDate(childText(item, "pubDate")),
                        null
                ));
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Không đọc được RSS từ " + source.getCode(), exception);
        }
    }

    private static String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private static String enclosureUrl(Element item) {
        NodeList nodes = item.getElementsByTagName("enclosure");
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        Node url = node.getAttributes() == null ? null : node.getAttributes().getNamedItem("url");
        return url == null ? null : url.getNodeValue();
    }
}
