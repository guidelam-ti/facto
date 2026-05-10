package com.guidelam.facto.web;

import com.guidelam.facto.auth.GoogleAuthService;
import com.guidelam.facto.auth.TokenStore;
import com.guidelam.facto.settings.AppSettingKeys;
import com.guidelam.facto.settings.SettingsService;
import com.guidelam.facto.web.dto.SetupForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SettingsService settings;
    private final GoogleAuthService authService;
    private final TokenStore tokenStore;

    @ModelAttribute("authorized")
    public Boolean authorized() {
        return tokenStore.isAuthorized();
    }

    @ModelAttribute("credentialsConfigured")
    public Boolean credentialsConfigured() {
        return authService.credentialsConfigured();
    }

    @ModelAttribute("driveFolderId")
    public String driveFolderId() {
        return settings.getOrDefault(AppSettingKeys.DRIVE_ROOT_FOLDER_ID, "");
    }

    @ModelAttribute("driveFolderName")
    public String driveFolderName() {
        return settings.getOrDefault(AppSettingKeys.DRIVE_ROOT_FOLDER_NAME, "");
    }

    @GetMapping
    public String index(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new SetupForm(
                    settings.getOrDefault(AppSettingKeys.GOOGLE_CLIENT_ID, ""),
                    settings.getOrDefault(AppSettingKeys.GOOGLE_CLIENT_SECRET, "")
            ));
        }
        return "setup/index";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") SetupForm form,
                       BindingResult bindingResult,
                       RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return "setup/index";
        }
        settings.set(AppSettingKeys.GOOGLE_CLIENT_ID, form.clientId().trim());
        settings.set(AppSettingKeys.GOOGLE_CLIENT_SECRET, form.clientSecret().trim());
        redirect.addFlashAttribute("info",
                "Identifiants Google enregistrés. Tu peux maintenant connecter ton compte.");
        return "redirect:/setup";
    }

    @GetMapping("/connect")
    public String connect(RedirectAttributes redirect) {
        if (!authService.credentialsConfigured()) {
            redirect.addFlashAttribute("error",
                    "Saisis d'abord client_id et client_secret avant de connecter.");
            return "redirect:/setup";
        }
        return "redirect:" + authService.buildAuthUrl();
    }

    @PostMapping("/disconnect")
    public String disconnect(RedirectAttributes redirect) {
        tokenStore.clear();
        redirect.addFlashAttribute("info", "Connexion Google révoquée localement.");
        return "redirect:/setup";
    }
}
