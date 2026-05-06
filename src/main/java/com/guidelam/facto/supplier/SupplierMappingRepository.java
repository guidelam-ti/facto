package com.guidelam.facto.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierMappingRepository extends JpaRepository<SupplierMapping, Long> {

    Optional<SupplierMapping> findByEmailAddress(String emailAddress);

    List<SupplierMapping> findBySupplierIsNullAndIgnoredFalse();

    List<SupplierMapping> findAllByOrderByEmailAddressAsc();
}
