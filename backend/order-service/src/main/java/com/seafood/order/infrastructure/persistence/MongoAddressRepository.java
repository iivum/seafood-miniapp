package com.seafood.order.infrastructure.persistence;

import com.seafood.order.domain.model.Address;
import com.seafood.order.domain.model.AddressRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository implementation for Address persistence.
 *
 * <p>Extends MongoRepository to inherit standard CRUD operations
 * and adds custom query methods for user-based address lookups.</p>
 *
 * @see Address
 * @see AddressRepository
 */
@Repository
public interface MongoAddressRepository extends AddressRepository, MongoRepository<Address, String> {

    /**
     * Find all addresses for a specific user.
     *
     * @param userId the user identifier
     * @return list of addresses belonging to the user
     */
    @Override
    List<Address> findByUserId(String userId);
}