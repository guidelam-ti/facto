package com.guidelam.facto.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthCallbackController {

    private final GoogleAuthService authService;

    @GetMapping("/callback")
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String error,
                           RedirectAttributes redirect) {
        if (error != null) {
            log.warn("Google OAuth callback returned error: {}", error);
            redirect.addFlashAttribute("error", "Autorisation refusée par Google : " + error);
            return "redirect:/setup";
        }
        if (code == null || code.isBlank()) {
            redirect.addFlashAttribute("error", "Code d'autorisation manquant dans le callback.");
            return "redirect:/setup";
        }
        try {
            authService.exchangeCodeAndStore(code);
            redirect.addFlashAttribute("success", "Compte Google connecté !");
        } catch (IOException | IllegalStateException e) {
            log.error("Failed to exchange OAuth code", e);
            redirect.addFlashAttribute("error",
                    "Erreur lors de l'échange du code : " + e.getMessage());
        }
        return "redirect:/setup";
    }
}
