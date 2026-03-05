package com.qanal.control.adapter.out.persistence;

import com.qanal.control.domain.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationJpaRepository extends JpaRepository<Organization, String> {

    Optional<Organization> findByStripeCustomerId(String stripeCustomerId);
}
