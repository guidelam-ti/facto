package com.guidelam.facto.web;

import com.guidelam.facto.auth.TokenStore;
import com.guidelam.facto.drive.DriveService;
import com.guidelam.facto.settings.AppSettingKeys;
import com.guidelam.facto.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/setup/drive")
@RequiredArgsConstructor
@Slf4j
public class SetupDriveController {

    private final SettingsService settings;
    private final TokenStore tokenStore;
    private final DriveService driveService;

    @GetMapping
    public String index(@RequestParam(required = false) String q,
                        Model model,
                        RedirectAttributes redirect) {
        if (!tokenStore.isAuthorized()) {
            redirect.addFlashAttribute("error",
                    "Connecte d'abord ton compte Google avant de choisir un dossier Drive.");
            return "redirect:/setup";
        }
        try {
            var folders = driveService.listFolders(q);
            model.addAttribute("folders", folders);
            model.addAttribute("query", q != null ? q : "");
            model.addAttribute("currentFolderId",
                    settings.getOrDefault(AppSettingKeys.DRIVE_ROOT_FOLDER_ID, ""));
            model.addAttribute("currentFolderName",
                    settings.getOrDefault(AppSettingKeys.DRIVE_ROOT_FOLDER_NAME, ""));
            return "setup/drive";
        } catch (IOException e) {
            log.error("Drive listFolders failed", e);
            redirect.addFlashAttribute("error",
                    "Échec de la liste des dossiers Drive : " + e.getMessage());
            return "redirect:/setup";
        }
    }

    @PostMapping("/select")
    public String select(@RequestParam String folderId,
                         @RequestParam String folderName,
                         RedirectAttributes redirect) {
        settings.set(AppSettingKeys.DRIVE_ROOT_FOLDER_ID, folderId);
        settings.set(AppSettingKeys.DRIVE_ROOT_FOLDER_NAME, folderName);
        redirect.addFlashAttribute("success",
                "Dossier Drive d'archivage sélectionné : " + folderName);
        return "redirect:/setup";
    }

    @PostMapping("/clear")
    public String clear(RedirectAttributes redirect) {
        settings.delete(AppSettingKeys.DRIVE_ROOT_FOLDER_ID);
        settings.delete(AppSettingKeys.DRIVE_ROOT_FOLDER_NAME);
        redirect.addFlashAttribute("info", "Sélection du dossier Drive effacée.");
        return "redirect:/setup";
    }
}
