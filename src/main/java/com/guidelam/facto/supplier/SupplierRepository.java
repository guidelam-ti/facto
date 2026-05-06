package com.guidelam.facto.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByCanonicalNameIgnoreCase(String canonicalName);

    List<Supplier> findAllByOrderByDisplayNameAsc();
}
