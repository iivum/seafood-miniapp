package com.seafood.order.infrastructure.persistence;

import com.seafood.order.domain.model.Address;
import com.seafood.order.domain.model.AddressRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MongoAddressRepository extends AddressRepository, MongoRepository<Address, String> {
    
    @Override
    List<Address> findByUserId(String userId);
}