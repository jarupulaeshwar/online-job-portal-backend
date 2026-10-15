package com.jobportal.repository;

import com.jobportal.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findFirstByName(String name);

    @Query("select c from Company c where :q = '' or lower(c.name) like lower(concat('%', :q, '%')) "
            + "or lower(c.industry) like lower(concat('%', :q, '%'))")
    Page<Company> search(@Param("q") String q, Pageable pageable);
}
