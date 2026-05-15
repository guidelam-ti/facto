package com.guidelam.facto.web;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/setup")
@Slf4j
public class SetupHelpController {

    private static final String GUIDE_RESOURCE = "docs/google-cloud-setup.md";

    private String googleCloudHtml = "";

    @PostConstruct
    void renderGuide() {
        ClassPathResource res = new ClassPathResource(GUIDE_RESOURCE);
        if (!res.exists()) {
            log.warn("Guide markdown introuvable sur le classpath : {}", GUIDE_RESOURCE);
            googleCloudHtml = "<p class=\"text-danger\">Guide indisponible : "
                    + GUIDE_RESOURCE + " absent du classpath.</p>";
            return;
        }
        try (InputStream in = res.getInputStream()) {
            String md = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<Extension> extensions = List.of(TablesExtension.create());
            Parser parser = Parser.builder().extensions(extensions).build();
            HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).build();
            Node doc = parser.parse(md);
            googleCloudHtml = renderer.render(doc);
            log.info("Guide Google Cloud rendu en HTML ({} caractères).", googleCloudHtml.length());
        } catch (IOException e) {
            log.error("Échec du rendu du guide Google Cloud", e);
            googleCloudHtml = "<p class=\"text-danger\">Erreur de lecture du guide : "
                    + e.getMessage() + "</p>";
        }
    }

    @GetMapping("/google-cloud")
    public String googleCloud(Model model) {
        model.addAttribute("html", googleCloudHtml);
        return "setup/google-cloud";
    }
}
