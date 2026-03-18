package com.seafood.order.interfaces.rest;

import com.seafood.order.application.AddressApplicationService;
import com.seafood.order.domain.model.Address;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
@Tag(name = "Address Management", description = "APIs for managing shipping addresses")
public class AddressController {
    private final AddressApplicationService addressApplicationService;

    @PostMapping
    @Operation(
        summary = "Create a new address",
        description = "Creates a new shipping address for the authenticated user",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Address details",
            required = true,
            content = @Content(schema = @Schema(implementation = CreateAddressRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Address created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
        }
    )
    public ResponseEntity<Address> createAddress(@RequestBody CreateAddressRequest request) {
        // 设置当前用户ID
        String userId = getCurrentUserId(); // 实际应从认证信息获取
        
        // 如果设置为默认地址，需要先取消其他默认地址
        if (request.isDefault()) {
            addressApplicationService.unsetDefaultAddress(userId);
        }
        
        Address address = addressApplicationService.createAddress(
            userId,
            request.getName(),
            request.getPhone(),
            request.getProvince(),
            request.getCity(),
            request.getDistrict(),
            request.getDetailAddress(),
            request.isDefault()
        );
        
        return ResponseEntity.ok(address);
    }

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Get user addresses",
        description = "Returns all shipping addresses for a specific user",
        parameters = @Parameter(name = "userId", description = "User ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Addresses found"),
            @ApiResponse(responseCode = "204", description = "No addresses found")
        }
    )
    public ResponseEntity<List<Address>> getUserAddresses(@PathVariable String userId) {
        List<Address> addresses = addressApplicationService.getUserAddresses(userId);
        if (addresses.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(addresses);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get address by ID",
        description = "Returns a specific address by its ID",
        parameters = @Parameter(name = "id", description = "Address ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Address found"),
            @ApiResponse(responseCode = "404", description = "Address not found")
        }
    )
    public ResponseEntity<Address> getAddressById(@PathVariable String id) {
        return addressApplicationService.getAddressById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update an address",
        description = "Updates an existing shipping address",
        parameters = @Parameter(name = "id", description = "Address ID", required = true),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Updated address details",
            required = true,
            content = @Content(schema = @Schema(implementation = UpdateAddressRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Address updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Address not found")
        }
    )
    public ResponseEntity<Address> updateAddress(@PathVariable String id, @RequestBody UpdateAddressRequest request) {
        // 如果设置为默认地址，需要先取消其他默认地址
        if (request.isDefault()) {
            String userId = getCurrentUserId();
            addressApplicationService.unsetDefaultAddress(userId);
        }
        
        Address updatedAddress = addressApplicationService.updateAddress(
            id,
            request.getName(),
            request.getPhone(),
            request.getProvince(),
            request.getCity(),
            request.getDistrict(),
            request.getDetailAddress(),
            request.isDefault()
        );
        
        return ResponseEntity.ok(updatedAddress);
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete an address",
        description = "Deletes a shipping address by its ID",
        parameters = @Parameter(name = "id", description = "Address ID", required = true),
        responses = {
            @ApiResponse(responseCode = "204", description = "Address deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Address not found")
        }
    )
    public ResponseEntity<Void> deleteAddress(@PathVariable String id) {
        addressApplicationService.deleteAddress(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/default")
    @Operation(
        summary = "Set default address",
        description = "Sets an address as the default shipping address for the user",
        parameters = @Parameter(name = "id", description = "Address ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Default address set successfully"),
            @ApiResponse(responseCode = "404", description = "Address not found")
        }
    )
    public ResponseEntity<Address> setDefaultAddress(@PathVariable String id) {
        String userId = getCurrentUserId();
        
        // 取消当前默认地址
        addressApplicationService.unsetDefaultAddress(userId);
        
        // 设置新的默认地址
        Address address = addressApplicationService.setDefaultAddress(id);
        
        return ResponseEntity.ok(address);
    }

    @GetMapping("/user/{userId}/default")
    @Operation(
        summary = "Get default address",
        description = "Returns the default shipping address for a user",
        parameters = @Parameter(name = "userId", description = "User ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Default address found"),
            @ApiResponse(responseCode = "404", description = "No default address found")
        }
    )
    public ResponseEntity<Address> getDefaultAddress(@PathVariable String userId) {
        return addressApplicationService.getDefaultAddress(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private String getCurrentUserId() {
        // 实际应从认证信息获取当前用户ID
        return "mock-user-123";
    }
}