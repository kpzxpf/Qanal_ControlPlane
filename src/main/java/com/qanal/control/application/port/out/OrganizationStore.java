package com.qanal.control.application.port.out;

import com.qanal.control.domain.model.Organization;

import java.util.Optional;

public interface OrganizationStore {

    Organization save(Organization org);

    Optional<Organization> findById(String id);

    Optional<Organization> findByStripeCustomerId(String stripeCustomerId);
}
