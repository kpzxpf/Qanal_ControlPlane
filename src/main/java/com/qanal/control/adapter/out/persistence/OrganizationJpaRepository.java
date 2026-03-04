package com.qanal.control.adapter.out.persistence;

import com.qanal.control.domain.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationJpaRepository extends JpaRepository<Organization, String> {
}
