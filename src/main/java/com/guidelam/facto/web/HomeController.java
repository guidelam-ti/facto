package com.guidelam.facto.web;

import com.guidelam.facto.auth.TokenStore;
import com.guidelam.facto.invoice.ProcessedInvoice;
import com.guidelam.facto.invoice.ProcessedInvoiceRepository;
import com.guidelam.facto.processing.ProcessingJob;
import com.guidelam.facto.processing.ProcessingJobRepository;
import com.guidelam.facto.processing.ProcessingJobType;
import com.guidelam.facto.settings.AppSettingKeys;
import com.guidelam.facto.settings.SettingsService;
import com.guidelam.facto.supplier.SupplierMappingRepository;
import com.guidelam.facto.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int RECENT_INVOICES_LIMIT = 5;

    private final ProcessedInvoiceRepository invoiceRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierMappingRepository mappingRepository;
    private final ProcessingJobRepository jobRepository;
    private final TokenStore tokenStore;
    private final SettingsService settings;

    @GetMapping("/")
    public String home(Model model) {
        long invoiceCount = invoiceRepository.count();
        long supplierCount = supplierRepository.count();
        long pendingMappings = mappingRepository.findBySupplierIsNullAndIgnoredFalse().size();

        ProcessingJob lastProcess = jobRepository
                .findFirstByTypeOrderByStartedAtDesc(ProcessingJobType.PROCESS)
                .orElse(null);

        List<ProcessedInvoice> recent = invoiceCount > 0
                ? invoiceRepository.findAllWithSupplierOrderByInvoiceDateDesc()
                        .stream().limit(RECENT_INVOICES_LIMIT).toList()
                : List.of();

        boolean authorized = tokenStore.isAuthorized();
        String driveFolderName = settings.getOrDefault(AppSettingKeys.DRIVE_ROOT_FOLDER_NAME, "");
        boolean driveFolderSelected = !driveFolderName.isBlank();

        model.addAttribute("invoiceCount", invoiceCount);
        model.addAttribute("supplierCount", supplierCount);
        model.addAttribute("pendingMappings", pendingMappings);
        model.addAttribute("lastProcess", lastProcess);
        model.addAttribute("recentInvoices", recent);
        model.addAttribute("authorized", authorized);
        model.addAttribute("driveFolderName", driveFolderName);
        model.addAttribute("driveFolderSelected", driveFolderSelected);

        return "home/index";
    }
}
