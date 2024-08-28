package searchengine.services;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class SiteMapGenerator extends RecursiveAction {
    private final String url;
    private final String domain;
    private final Set<String> visited;
    private final int level;
    private static final String TAB = "\t";

    public SiteMapGenerator(String url, String domain, Set<String> visited, int level) {
        this.url = url;
        this.domain = domain;
        this.visited = visited;
        this.level = level;
    }

    @Override
    protected void compute() {
        if (!visited.contains(url) && url.contains(domain)) {
            try {
                visited.add(url);
                Document doc = Jsoup.connect(url).get();
                Files.write(Paths.get("sitemap.txt"), (String.join("", Collections.nCopies(level, TAB)) + url + "\n").getBytes(), StandardOpenOption.APPEND);
                Thread.sleep(100 + (int)(Math.random() * 50));
                Elements links = doc.select("a[href]");
                List<SiteMapGenerator> tasks = new ArrayList<>();
                for (Element link : links) {
                    String childUrl = link.absUrl("href");
                    if (!childUrl.contains("#") && childUrl.contains(domain)) {
                        tasks.add(new SiteMapGenerator(childUrl, domain, visited, level + 1));
                    }
                }
                invokeAll(tasks);
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Set<String> visited = new HashSet<>();
        SiteMapGenerator task = new SiteMapGenerator("https://lenta.ru/", "lenta.ru", visited, 0);
        ForkJoinPool.commonPool().invoke(task);
    }
}

