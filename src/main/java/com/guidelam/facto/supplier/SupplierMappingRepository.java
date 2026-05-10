package com.guidelam.facto.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierMappingRepository extends JpaRepository<SupplierMapping, Long> {

    Optional<SupplierMapping> findByEmailAddress(String emailAddress);

    /**
     * Variant that eagerly fetches the supplier so callers can dereference
     * {@code mapping.getSupplier().getCanonicalName()} after the session closes.
     */
    @Query("SELECT m FROM SupplierMapping m LEFT JOIN FETCH m.supplier WHERE m.emailAddress = :email")
    Optional<SupplierMapping> findByEmailAddressFetchSupplier(@Param("email") String email);

    List<SupplierMapping> findBySupplierIsNullAndIgnoredFalse();

    List<SupplierMapping> findBySupplierIsNullAndIgnoredFalseOrderByMessageCountDescEmailAddressAsc();

    List<SupplierMapping> findAllByOrderByEmailAddressAsc();

    /**
     * Returns mappings the user has already decided about (mapped to a supplier OR
     * marked as ignored), with the {@code supplier} association eagerly fetched so
     * Thymeleaf can dereference {@code m.supplier.displayName} after the session
     * is closed (we keep open-in-view disabled).
     */
    @Query("SELECT m FROM SupplierMapping m " +
            "LEFT JOIN FETCH m.supplier " +
            "WHERE m.supplier IS NOT NULL OR m.ignored = TRUE " +
            "ORDER BY m.messageCount DESC, m.emailAddress ASC")
    List<SupplierMapping> findDecidedWithSupplier();
}
