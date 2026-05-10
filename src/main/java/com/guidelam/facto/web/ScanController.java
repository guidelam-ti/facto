package com.guidelam.facto.web;

import com.guidelam.facto.auth.TokenStore;
import com.guidelam.facto.processing.ProcessingJob;
import com.guidelam.facto.processing.ProcessingJobRepository;
import com.guidelam.facto.processing.ProcessingJobType;
import com.guidelam.facto.processing.ProcessingStatus;
import com.guidelam.facto.processing.ScanService;
import com.guidelam.facto.settings.AppSettingKeys;
import com.guidelam.facto.settings.SettingsService;
import com.guidelam.facto.supplier.SupplierMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/scan")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;
    private final ProcessingJobRepository jobRepository;
    private final SupplierMappingRepository mappingRepository;
    private final TokenStore tokenStore;
    private final SettingsService settings;

    @GetMapping
    public String index(Model model) {
        ProcessingJob lastScan = jobRepository
                .findFirstByTypeOrderByStartedAtDesc(ProcessingJobType.SCAN)
                .orElse(null);

        long pendingCount = mappingRepository.findBySupplierIsNullAndIgnoredFalse().size();

        model.addAttribute("lastScan", lastScan);
        model.addAttribute("authorized", tokenStore.isAuthorized());
        model.addAttribute("driveFolderName",
                settings.getOrDefault(AppSettingKeys.DRIVE_ROOT_FOLDER_NAME, ""));
        model.addAttribute("pendingMappingsCount", pendingCount);
        model.addAttribute("isRunning",
                lastScan != null && lastScan.getStatus() == ProcessingStatus.RUNNING);
        return "scan/index";
    }

    @PostMapping("/start")
    public String start(RedirectAttributes redirect) {
        if (!tokenStore.isAuthorized()) {
            redirect.addFlashAttribute("error",
                    "Connecte d'abord ton compte Google avant de scanner.");
            return "redirect:/setup";
        }
        try {
            ProcessingJob job = scanService.startScan();
            return "redirect:/scan/progress/" + job.getId();
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/scan";
        }
    }

    @GetMapping("/progress/{jobId}")
    public String progress(@PathVariable Long jobId, Model model) {
        ProcessingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return "redirect:/scan";
        }
        model.addAttribute("jobId", jobId);
        model.addAttribute("job", job);
        return "scan/progress";
    }
}
