package com.seafood.order.interfaces.rest.mapper;

import com.seafood.order.domain.model.Address;
import com.seafood.order.domain.model.Order;
import com.seafood.order.domain.model.OrderHistory;
import com.seafood.order.domain.model.OrderItem;
import com.seafood.order.interfaces.rest.dto.AddressResponse;
import com.seafood.order.interfaces.rest.dto.OrderHistoryResponse;
import com.seafood.order.interfaces.rest.dto.OrderItemResponse;
import com.seafood.order.interfaces.rest.dto.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * MapStruct mapper for converting between Order domain models and DTOs.
 *
 * <p>This mapper handles the translation between the internal domain layer
 * (Order, OrderItem, etc.) and the external interface layer (DTOs).
 * It includes custom converters for type transformations like Date to String
 * and double to BigDecimal for API compatibility.</p>
 *
 * @see Order
 * @see OrderResponse
 * @see OrderItem
 * @see OrderItemResponse
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "totalPrice", source = "totalPrice", qualifiedByName = "doubleToBigDecimal")
    @Mapping(target = "shippingFee", source = "shippingFee", qualifiedByName = "doubleToBigDecimal")
    @Mapping(target = "discountAmount", source = "discountAmount", qualifiedByName = "doubleToBigDecimal")
    @Mapping(target = "finalPrice", source = "finalPrice", qualifiedByName = "doubleToBigDecimal")
    @Mapping(target = "items", source = "items")
    @Mapping(target = "shippingAddress", source = "shippingAddress")
    @Mapping(target = "orderHistory", source = "orderHistory")
    @Mapping(target = "status", source = "status", qualifiedByName = "orderStatusToString")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "dateToString")
    @Mapping(target = "paidAt", source = "paidAt", qualifiedByName = "dateToString")
    @Mapping(target = "shippedAt", source = "shippedAt", qualifiedByName = "dateToString")
    @Mapping(target = "deliveredAt", source = "deliveredAt", qualifiedByName = "dateToString")
    OrderResponse toResponse(Order order);

    @Named("doubleToBigDecimal")
    default BigDecimal doubleToBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    @Named("orderStatusToString")
    default String orderStatusToString(com.seafood.order.domain.model.OrderStatus status) {
        return status != null ? status.name() : null;
    }

    @Named("dateToString")
    default String dateToString(Date date) {
        if (date == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    @Mapping(target = "price", source = "price", qualifiedByName = "doubleToBigDecimal")
    @Mapping(target = "totalPrice", source = "totalPrice", qualifiedByName = "doubleToBigDecimal")
    OrderItemResponse toItemResponse(OrderItem item);

    @Mapping(target = "receiverName", source = "name")
    @Mapping(target = "address", source = "detailAddress")
    AddressResponse toAddressResponse(Address address);

    @Mapping(target = "status", source = "status", qualifiedByName = "orderStatusToString")
    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "dateToString")
    OrderHistoryResponse toHistoryResponse(OrderHistory history);
}
