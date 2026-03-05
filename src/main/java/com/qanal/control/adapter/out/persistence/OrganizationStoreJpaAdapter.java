package com.qanal.control.adapter.out.persistence;

import com.qanal.control.application.port.out.OrganizationStore;
import com.qanal.control.domain.model.Organization;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrganizationStoreJpaAdapter implements OrganizationStore {

    private final OrganizationJpaRepository repo;

    public OrganizationStoreJpaAdapter(OrganizationJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Organization save(Organization org) {
        return repo.save(org);
    }

    @Override
    public Optional<Organization> findById(String id) {
        return repo.findById(id);
    }

    @Override
    public Optional<Organization> findByStripeCustomerId(String stripeCustomerId) {
        return repo.findByStripeCustomerId(stripeCustomerId);
    }
}
