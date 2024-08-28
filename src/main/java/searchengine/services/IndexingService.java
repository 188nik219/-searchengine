package searchengine.services;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
//import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.model.PageRepository;
import searchengine.model.SiteRepository;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesList = new SitesList();
    }

    @Transactional
    public void reindexSites() {
        List<searchengine.config.Site> sites = sitesList.getSites();
        for (searchengine.config.Site siteConfig : sites) {
            try {
                // Удаление существующих данных
                siteRepository.deleteByUrl(siteConfig.getUrl());

                // Создание новой записи о сайте
                Site site = new Site();
                site.setUrl(siteConfig.getUrl());
                site.setName(siteConfig.getName());
                site.setStatus(Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);

                // Запуск индексации страниц
                ForkJoinPool pool = new ForkJoinPool();
                pool.invoke(new PageIndexer(site, "/", pageRepository, siteRepository));
                pool.shutdown();

                // Обновление статуса на INDEXED
                site.setStatus(Status.INDEXED);
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);

            } catch (Exception e) {
                // Обработка ошибки и обновление статуса на FAILED
                Site site = siteRepository.findByUrl(siteConfig.getUrl())
                        .orElseThrow(() -> new EntityNotFoundException("Site not found: " + siteConfig.getUrl()));
                site.setStatus(Status.FAILED);
                site.setLastError(e.getMessage());
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        }
    }

    private static class PageIndexer extends RecursiveTask<Void> {
        private final Site site;
        private final String path;
        private final PageRepository pageRepository;
        private final SiteRepository siteRepository;

        public PageIndexer(Site site, String path, PageRepository pageRepository, SiteRepository siteRepository) {
            this.site = site;
            this.path = path;
            this.pageRepository = pageRepository;
            this.siteRepository = siteRepository;
        }

        @Override
        protected Void compute() {
            try {
                Document document = Jsoup.connect(site.getUrl() + path).get();
                int code = document.connection().response().statusCode();
                String content = document.html();

                // Сохранение страницы в базе данных
                Page page = new Page();
                page.setSite(site);
                page.setPath(path);
                page.setCode(code);
                page.setContent(content);
                pageRepository.save(page);

                // Обновление времени статуса
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);

                // Поиск и индексация всех ссылок на странице
                Elements links = document.select("a[href]");
                List<PageIndexer> tasks = new ArrayList<>();
                for (Element link : links) {
                    String href = link.attr("href");
                    if (href.startsWith("/")) {
                        tasks.add(new PageIndexer(site, href, pageRepository, siteRepository));
                    }
                }
                invokeAll(tasks);

            } catch (IOException e) {
                // Обработка ошибок при подключении
                site.setStatus(Status.FAILED);
                site.setLastError("Error indexing page " + path + ": " + e.getMessage());
                siteRepository.save(site);
            }
            return null;
        }
    }
}
