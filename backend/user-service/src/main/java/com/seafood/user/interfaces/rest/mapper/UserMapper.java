package com.seafood.user.interfaces.rest.mapper;

import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRole;
import com.seafood.user.interfaces.rest.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", source = "role", qualifiedByName = "userRoleToString")
    UserResponse toResponse(User user);

    @Named("userRoleToString")
    default String userRoleToString(UserRole role) {
        return role != null ? role.name() : null;
    }
}
