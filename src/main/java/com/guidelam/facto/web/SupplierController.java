package com.guidelam.facto.web;

import com.guidelam.facto.supplier.Supplier;
import com.guidelam.facto.supplier.SupplierMapping;
import com.guidelam.facto.supplier.SupplierMappingRepository;
import com.guidelam.facto.supplier.SupplierRepository;
import com.guidelam.facto.supplier.SupplierService;
import com.guidelam.facto.web.dto.SupplierDecisionForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierRepository supplierRepository;
    private final SupplierMappingRepository mappingRepository;

    @GetMapping
    public String list(Model model) {
        List<SupplierMapping> allMappings = mappingRepository.findAllWithSupplier();

        long pendingCount = allMappings.stream()
                .filter(m -> m.getSupplier() == null && !m.isIgnored())
                .count();
        long mappedCount = allMappings.stream()
                .filter(m -> m.getSupplier() != null)
                .count();
        long ignoredCount = allMappings.stream()
                .filter(m -> m.getSupplier() == null && m.isIgnored())
                .count();

        List<Supplier> allSuppliers = supplierRepository.findAllByOrderByDisplayNameAsc();

        model.addAttribute("allMappings", allMappings);
        model.addAttribute("totalCount", allMappings.size());
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("mappedCount", mappedCount);
        model.addAttribute("ignoredCount", ignoredCount);
        model.addAttribute("allSuppliers", allSuppliers);
        model.addAttribute("decisionForm", new SupplierDecisionForm());
        return "supplier/list";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("decisionForm") SupplierDecisionForm form,
                       RedirectAttributes redirect) {
        int applied = supplierService.applyDecisions(form.getDecisions());
        if (applied == 0) {
            redirect.addFlashAttribute("info", "Aucune décision à enregistrer.");
        } else {
            redirect.addFlashAttribute("success",
                    String.format("%d décision(s) enregistrée(s).", applied));
        }
        return "redirect:/suppliers";
    }
}
