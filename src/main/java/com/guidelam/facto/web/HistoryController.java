package com.guidelam.facto.web;

import com.guidelam.facto.invoice.ProcessedInvoice;
import com.guidelam.facto.invoice.ProcessedInvoiceRepository;
import com.guidelam.facto.supplier.Supplier;
import com.guidelam.facto.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {

    private final ProcessedInvoiceRepository invoiceRepository;
    private final SupplierRepository supplierRepository;

    @GetMapping
    public String list(Model model) {
        List<ProcessedInvoice> invoices = invoiceRepository.findAllWithSupplierOrderByInvoiceDateDesc();

        // Distinct years from invoice dates (descending) — for the year filter dropdown
        List<Integer> years = invoices.stream()
                .map(i -> i.getInvoiceDate().getYear())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        // Suppliers that actually have invoices — for the supplier filter dropdown
        List<Supplier> suppliers = invoices.stream()
                .map(ProcessedInvoice::getSupplier)
                .distinct()
                .sorted(Comparator.comparing(Supplier::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        model.addAttribute("invoices", invoices);
        model.addAttribute("totalCount", invoices.size());
        model.addAttribute("years", years);
        model.addAttribute("suppliers", suppliers);
        return "history/index";
    }
}
