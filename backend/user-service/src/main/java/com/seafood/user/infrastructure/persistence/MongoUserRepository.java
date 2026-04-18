package com.seafood.user.infrastructure.persistence;

import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRepository;
import com.seafood.user.domain.model.UserRole;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MongoUserRepository implements UserRepository {

    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION_NAME = "users";

    public MongoUserRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public User save(User user) {
        return mongoTemplate.save(user, COLLECTION_NAME);
    }

    @Override
    public Optional<User> findById(String id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        User user = mongoTemplate.findOne(query, User.class, COLLECTION_NAME);
        return Optional.ofNullable(user);
    }

    @Override
    public Optional<User> findByOpenId(String openId) {
        Query query = Query.query(Criteria.where("openId").is(openId));
        User user = mongoTemplate.findOne(query, User.class, COLLECTION_NAME);
        return Optional.ofNullable(user);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        Query query = Query.query(Criteria.where("email").is(email));
        User user = mongoTemplate.findOne(query, User.class, COLLECTION_NAME);
        return Optional.ofNullable(user);
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return findAll().stream()
                .filter(user -> phone.equals(user.getPhone()))
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return mongoTemplate.findAll(User.class, COLLECTION_NAME);
    }

    @Override
    public List<User> findAll(String sortBy, String sortDir) {
        org.springframework.data.domain.Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? org.springframework.data.domain.Sort.Direction.ASC
                : org.springframework.data.domain.Sort.Direction.DESC;
        String validSortField = getValidSortField(sortBy);
        Query query = new Query().with(org.springframework.data.domain.Sort.by(direction, validSortField));
        return mongoTemplate.find(query, User.class, COLLECTION_NAME);
    }

    @Override
    public void deleteById(String id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, User.class, COLLECTION_NAME);
    }

    @Override
    public List<User> findByRole(UserRole role) {
        Query query = Query.query(Criteria.where("role").is(role));
        return mongoTemplate.find(query, User.class, COLLECTION_NAME);
    }

    @Override
    public List<User> findByRole(UserRole role, String sortBy, String sortDir) {
        org.springframework.data.domain.Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? org.springframework.data.domain.Sort.Direction.ASC
                : org.springframework.data.domain.Sort.Direction.DESC;
        String validSortField = getValidSortField(sortBy);
        Query query = Query.query(Criteria.where("role").is(role))
                .with(org.springframework.data.domain.Sort.by(direction, validSortField));
        return mongoTemplate.find(query, User.class, COLLECTION_NAME);
    }

    private String getValidSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        switch (sortBy.toLowerCase()) {
            case "nickname":
            case "name":
                return "nickname";
            case "email":
                return "email";
            case "phone":
                return "phone";
            case "role":
                return "role";
            case "createdat":
            case "created_at":
                return "createdAt";
            default:
                return "createdAt";
        }
    }
}
