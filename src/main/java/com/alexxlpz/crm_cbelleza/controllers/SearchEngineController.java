package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.config.SiteModelAdvice;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que leen los buscadores: {@code /sitemap.xml} con las páginas públicas y la ficha de cada centro publicado
 * ({@code /c/nombre}), y {@code /robots.txt} (el de static/robots.txt más la dirección del sitemap, que tiene que ser
 * absoluta y depende de SITE_URL).
 */
@Controller
public class SearchEngineController {

    /** Páginas públicas fijas que interesa que Google encuentre. */
    static final List<String> PAGES = List.of("/", "/centers", "/register-center", "/contact");

    private final CenterService centers;
    private final SiteModelAdvice site;

    public SearchEngineController(CenterService centers, SiteModelAdvice site) {
        this.centers = centers;
        this.site = site;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        String base = site.siteUrl();
        List<String> paths = new ArrayList<>(PAGES);
        centers.publishedSlugs().forEach(slug -> paths.add("/c/" + slug));
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        paths.forEach(path -> xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(base + path)).append("</loc></url>\n"));
        return xml.append("</urlset>\n").toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() throws IOException {
        String rules = new ClassPathResource("static/robots.txt").getContentAsString(StandardCharsets.UTF_8).stripTrailing();
        return rules + "\n\nSitemap: " + site.siteUrl() + "/sitemap.xml\n";
    }
}
