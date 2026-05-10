package com.guidelam.facto.supplier;

import com.guidelam.facto.web.dto.MappingDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMappingRepository mappingRepository;

    public Supplier findOrCreateByCanonicalName(String canonicalName, String displayName) {
        return supplierRepository.findByCanonicalNameIgnoreCase(canonicalName)
                .orElseGet(() -> supplierRepository.save(new Supplier(canonicalName, displayName)));
    }

    public int applyDecisions(List<MappingDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) return 0;
        int count = 0;
        for (MappingDecision decision : decisions) {
            if (decision == null) continue;
            if (decision.getMappingId() == null) continue;
            String action = decision.getAction();
            if (action == null || action.isBlank()) continue;

            SupplierMapping mapping = mappingRepository.findById(decision.getMappingId()).orElse(null);
            if (mapping == null) continue;

            boolean applied = applyOne(mapping, action.trim(), decision.getNewSupplierName());
            if (applied) {
                mappingRepository.save(mapping);
                count++;
            }
        }
        return count;
    }

    private boolean applyOne(SupplierMapping mapping, String action, String newName) {
        return switch (action) {
            case "ignore" -> {
                mapping.setSupplier(null);
                mapping.setIgnored(true);
                yield true;
            }
            case "reset" -> {
                mapping.setSupplier(null);
                mapping.setIgnored(false);
                yield true;
            }
            case "create" -> {
                if (newName == null || newName.isBlank()) {
                    log.warn("Skip 'create' decision for mapping {}: empty supplier name", mapping.getId());
                    yield false;
                }
                String trimmed = newName.trim();
                Supplier supplier = findOrCreateByCanonicalName(trimmed, trimmed);
                mapping.setSupplier(supplier);
                mapping.setIgnored(false);
                yield true;
            }
            default -> {
                try {
                    long supplierId = Long.parseLong(action);
                    Supplier supplier = supplierRepository.findById(supplierId).orElse(null);
                    if (supplier == null) {
                        log.warn("Skip mapping {}: supplier id {} not found", mapping.getId(), supplierId);
                        yield false;
                    }
                    mapping.setSupplier(supplier);
                    mapping.setIgnored(false);
                    yield true;
                } catch (NumberFormatException e) {
                    log.warn("Skip mapping {}: unknown action '{}'", mapping.getId(), action);
                    yield false;
                }
            }
        };
    }
}
