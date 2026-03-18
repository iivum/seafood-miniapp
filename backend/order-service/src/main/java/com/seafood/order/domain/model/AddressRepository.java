package com.seafood.order.domain.model;

import java.util.List;
import java.util.Optional;

public interface AddressRepository {
    Address save(Address address);
    Optional<Address> findById(String id);
    List<Address> findByUserId(String userId);
    void delete(Address address);
    List<Address> findAll();
}