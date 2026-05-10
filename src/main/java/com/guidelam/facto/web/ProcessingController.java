package com.guidelam.facto.web;

import com.guidelam.facto.auth.TokenStore;
import com.guidelam.facto.invoice.ProcessedInvoiceRepository;
import com.guidelam.facto.processing.ProcessService;
import com.guidelam.facto.processing.ProcessingJob;
import com.guidelam.facto.processing.ProcessingJobRepository;
import com.guidelam.facto.processing.ProcessingJobType;
import com.guidelam.facto.processing.ResetService;
import com.guidelam.facto.settings.AppSettingKeys;
import com.guidelam.facto.settings.SettingsService;
import com.guidelam.facto.supplier.SupplierMappingRepository;
import com.guidelam.facto.web.dto.PeriodForm;
import com.guidelam.facto.web.dto.ResetForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/process")
@RequiredArgsConstructor
public class ProcessingController {

    private final ProcessService processService;
    private final ResetService resetService;
    private final ProcessingJobRepository jobRepository;
    private final ProcessedInvoiceRepository invoiceRepository;
    private final SupplierMappingRepository mappingRepository;
    private final TokenStore tokenStore;
    private final SettingsService settings;

    @GetMapping
    public String index(Model model) {
        ProcessingJob lastJob = jobRepository
                .findFirstByTypeOrderByStartedAtDesc(ProcessingJobType.PROCESS)
                .orElse(null);

        long pendingMappings = mappingRepository
                .findBySupplierIsNullAndIgnoredFalse().size();

        boolean authorized = tokenStore.isAuthorized();
        String driveFolderName = settings.getOrDefault(AppSettingKeys.DRIVE_ROOT_FOLDER_NAME, "");
        boolean driveFolderSelected = !driveFolderName.isBlank();

        if (!model.containsAttribute("form")) {
            model.addAttribute("form", defaultForm());
        }
        if (!model.containsAttribute("resetForm")) {
            model.addAttribute("resetForm", defaultResetForm(lastJob));
        }
        model.addAttribute("authorized", authorized);
        model.addAttribute("driveFolderName", driveFolderName);
        model.addAttribute("driveFolderSelected", driveFolderSelected);
        model.addAttribute("pendingMappingsCount", pendingMappings);
        model.addAttribute("lastJob", lastJob);
        model.addAttribute("readyToStart", authorized && driveFolderSelected);
        model.addAttribute("invoiceCount", invoiceRepository.count());
        return "process/index";
    }

    @PostMapping("/reset")
    public String reset(@ModelAttribute("resetForm") ResetForm form,
                        RedirectAttributes redirect) {
        if (!tokenStore.isAuthorized()) {
            redirect.addFlashAttribute("error",
                    "Connecte d'abord ton compte Google avant de lancer un nettoyage.");
            return "redirect:/setup";
        }
        LocalDate start = null;
        LocalDate end = null;
        if ("period".equals(form.getMode())) {
            start = form.getPeriodStart();
            end = form.getPeriodEnd();
            if (start == null || end == null) {
                redirect.addFlashAttribute("error",
                        "Mode période : choisis une date de début et une date de fin.");
                redirect.addFlashAttribute("resetForm", form);
                return "redirect:/process";
            }
            if (start.isAfter(end)) {
                redirect.addFlashAttribute("error",
                        "La date de fin doit être supérieure ou égale à la date de début.");
                redirect.addFlashAttribute("resetForm", form);
                return "redirect:/process";
            }
        }
        try {
            ProcessingJob job = resetService.startReset(start, end);
            return "redirect:/process/reset/progress/" + job.getId();
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("resetForm", form);
            return "redirect:/process";
        }
    }

    @GetMapping("/reset/progress/{jobId}")
    public String resetProgress(@PathVariable Long jobId, Model model) {
        ProcessingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return "redirect:/process";
        }
        model.addAttribute("jobId", jobId);
        model.addAttribute("job", job);
        return "process/reset-progress";
    }

    @PostMapping("/start")
    public String start(@Valid @ModelAttribute("form") PeriodForm form,
                        BindingResult bindingResult,
                        RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            redirect.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
            redirect.addFlashAttribute("form", form);
            return "redirect:/process";
        }
        try {
            ProcessingJob job = processService.startProcess(form.getPeriodStart(), form.getPeriodEnd());
            return "redirect:/process/progress/" + job.getId();
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("form", form);
            return "redirect:/process";
        }
    }

    @GetMapping("/progress/{jobId}")
    public String progress(@PathVariable Long jobId, Model model) {
        ProcessingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return "redirect:/process";
        }
        model.addAttribute("jobId", jobId);
        model.addAttribute("job", job);
        return "process/progress";
    }

    private PeriodForm defaultForm() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);
        LocalDate firstOfPrevMonth = firstOfThisMonth.minusMonths(1);
        LocalDate lastOfPrevMonth = firstOfThisMonth.minusDays(1);
        return new PeriodForm(firstOfPrevMonth, lastOfPrevMonth);
    }

    private ResetForm defaultResetForm(ProcessingJob lastJob) {
        LocalDate start = lastJob != null ? lastJob.getPeriodStart() : null;
        LocalDate end = lastJob != null ? lastJob.getPeriodEnd() : null;
        if (start == null || end == null) {
            LocalDate today = LocalDate.now();
            LocalDate firstOfThisMonth = today.withDayOfMonth(1);
            start = firstOfThisMonth.minusMonths(1);
            end = firstOfThisMonth.minusDays(1);
        }
        return new ResetForm("all", start, end);
    }
}
