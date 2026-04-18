package com.seafood.product.interfaces.rest.mapper;

import com.seafood.product.domain.model.Product;
import com.seafood.product.interfaces.rest.dto.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductMapper INSTANCE = Mappers.getMapper(ProductMapper.class);

    ProductResponse toResponse(Product product);
}
