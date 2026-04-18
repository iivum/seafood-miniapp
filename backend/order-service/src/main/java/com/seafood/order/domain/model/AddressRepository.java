package com.seafood.order.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Address aggregate persistence.
 *
 * <p>Defines the contract for storing and retrieving shipping addresses.
 * Implementations handle the specific details of the persistence mechanism
 * (MongoDB in this case) while keeping the domain layer agnostic.</p>
 *
 * @see Address
 * @see com.seafood.order.infrastructure.persistence.MongoAddressRepository
 */
public interface AddressRepository {

    /**
     * Persist an address (insert or update).
     *
     * @param address the address to save
     * @return the saved address with any generated ID
     */
    Address save(Address address);

    /**
     * Find an address by its unique identifier.
     *
     * @param id the address ID
     * @return Optional containing the address if found
     */
    Optional<Address> findById(String id);

    /**
     * Find all addresses belonging to a specific user.
     *
     * @param userId the user's identifier
     * @return list of addresses for the user
     */
    List<Address> findByUserId(String userId);

    /**
     * Delete an address from storage.
     *
     * @param address the address to delete
     */
    void delete(Address address);

    /**
     * Retrieve all addresses in the system (admin use).
     *
     * @return list of all addresses
     */
    List<Address> findAll();
}