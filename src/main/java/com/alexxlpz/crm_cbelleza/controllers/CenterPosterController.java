package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.config.SiteModelAdvice;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.OpeningHoursService;
import com.alexxlpz.crm_cbelleza.services.QrCodeService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

/**
 * Cartel con QR para el mostrador («Mi Centro» > Enlace para reservar > Cartel con QR).
 * El QR lleva al enlace corto del centro ({@code /c/nombre}), que abre su ficha pública sin iniciar sesión.
 * Lo puede imprimir o descargar cualquier trabajador del centro: no cambia nada, solo enseña datos públicos.
 */
@Controller
public class CenterPosterController {

    private final CenterService centerService;
    private final OpeningHoursService openingHours;
    private final QrCodeService qr;
    private final SiteModelAdvice site;

    public CenterPosterController(CenterService centerService, OpeningHoursService openingHours,
                                  QrCodeService qr, SiteModelAdvice site) {
        this.centerService = centerService;
        this.openingHours = openingHours;
        this.qr = qr;
        this.site = site;
    }

    @GetMapping("/worker/center/cartel")
    public String poster(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        Center center = centerService.getCenterById(worker.getCenterId());
        String link = shortLink(center);
        model.addAttribute("center", center);
        model.addAttribute("shortLink", link);
        // Sin «https://» se lee mejor en papel y la cámara no lo necesita
        model.addAttribute("shortLinkText", link.replaceFirst("^https?://", ""));
        model.addAttribute("qrSvg", qr.svg(link, "Código QR para reservar en " + center.getName()));
        model.addAttribute("openingHoursLines", openingHours.forCenter(center.getId()).describe());
        model.addAttribute("slug", centerService.shortSlug(center));
        model.addAttribute("activePage", "center");
        return "worker/center_poster";
    }

    @GetMapping("/worker/center/qr.svg")
    public ResponseEntity<byte[]> svg(@AuthenticationPrincipal AppUserDetails worker) {
        Center center = centerService.getCenterById(worker.getCenterId());
        byte[] body = qr.svg(shortLink(center), "Código QR para reservar en " + center.getName())
                .getBytes(StandardCharsets.UTF_8);
        return download(center, "svg", MediaType.valueOf("image/svg+xml"), body);
    }

    @GetMapping("/worker/center/qr.png")
    public ResponseEntity<byte[]> png(@AuthenticationPrincipal AppUserDetails worker) {
        Center center = centerService.getCenterById(worker.getCenterId());
        return download(center, "png", MediaType.IMAGE_PNG, qr.png(shortLink(center)));
    }

    private String shortLink(Center center) {
        return site.siteUrl() + "/c/" + centerService.shortSlug(center);
    }

    private ResponseEntity<byte[]> download(Center center, String extension, MediaType type, byte[] body) {
        String fileName = "qr-" + centerService.shortSlug(center) + "." + extension;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}
