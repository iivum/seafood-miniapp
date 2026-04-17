package com.seafood.product.interfaces.rest;

import com.seafood.product.application.ProductApplicationService;
import com.seafood.product.domain.model.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for product management operations.
 * Provides endpoints for creating, retrieving, updating and deleting seafood products.
 * All endpoints return proper HTTP status codes and JSON responses.
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Product Management", description = "APIs for managing seafood products")
public class ProductController {
    private final ProductApplicationService productApplicationService;

    @PostMapping
    @Operation(
        summary = "Create a new product",
        description = "Creates a new seafood product with the provided details",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Product details",
            required = true,
            content = @Content(schema = @Schema(implementation = CreateProductRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
        }
    )
    public ResponseEntity<Product> createProduct(@RequestBody CreateProductRequest request) {
        Product product = productApplicationService.createProduct(request.getName(), request.getDescription(), request.getPrice(), request.getStock(), request.getCategory(), request.getImageUrl());
        return ResponseEntity.ok(product);
    }

    /**
     * 获取商品列表（分页）
     *
     * @param page     页码（默认 0）
     * @param pageSize 每页数量（默认 10）
     * @param category 分类筛选（可选）
     * @param keyword  搜索关键词（可选）
     * @return 分页商品列表
     */
    @GetMapping
    @Operation(
        summary = "List products with pagination",
        description = "Returns a paginated list of products with optional filtering",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters")
        }
    )
    public ResponseEntity<Map<String, Object>> listProducts(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "Category filter") @RequestParam(required = false) String category,
            @Parameter(description = "Search keyword") @RequestParam(required = false) String keyword) {

        // 参数验证
        if (page < 0) {
            return ResponseEntity.badRequest().build();
        }
        if (pageSize <= 0 || pageSize > 100) {
            return ResponseEntity.badRequest().build();
        }

        // 构建分页请求，按 ID 降序排序
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "id"));

        // 获取分页数据
        Page<Product> productPage = productApplicationService.listProducts(keyword, category, pageable);

        // 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("products", productPage.getContent());
        response.put("page", productPage.getNumber());
        response.put("totalPages", productPage.getTotalPages());
        response.put("totalProducts", productPage.getTotalElements());
        response.put("hasNext", productPage.hasNext());
        response.put("hasPrev", productPage.hasPrevious());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    @Operation(
        summary = "List all products",
        description = "Returns a list of all available seafood products",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "204", description = "No products found")
        }
    )
    public ResponseEntity<List<Product>> listAllProducts() {
        return ResponseEntity.ok(productApplicationService.listAllProducts());
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get product by ID",
        description = "Returns a specific product by its ID",
        parameters = @Parameter(name = "id", description = "Product ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
        }
    )
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        return productApplicationService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    @Operation(
        summary = "Find products by category",
        description = "Returns all products in a specific category",
        parameters = @Parameter(name = "category", description = "Product category", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Products found"),
            @ApiResponse(responseCode = "204", description = "No products found in this category")
        }
    )
    public ResponseEntity<List<Product>> findByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productApplicationService.findByCategory(category));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a product",
        description = "Deletes a product by its ID",
        parameters = @Parameter(name = "id", description = "Product ID", required = true),
        responses = {
            @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
        }
    )
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productApplicationService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a product",
        description = "Updates an existing product with the provided details",
        parameters = @Parameter(name = "id", description = "Product ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
        }
    )
    public ResponseEntity<Product> updateProduct(
            @PathVariable String id,
            @RequestBody CreateProductRequest request) {
        Product product = productApplicationService.updateProduct(
            id, request.getName(), request.getDescription(),
            request.getPrice(), request.getStock(),
            request.getCategory(), request.getImageUrl());
        return ResponseEntity.ok(product);
    }

    @PatchMapping("/{id}")
    @Operation(
        summary = "Partially update a product",
        description = "Updates specific fields of an existing product",
        parameters = @Parameter(name = "id", description = "Product ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
        }
    )
    public ResponseEntity<Product> patchProduct(
            @PathVariable String id,
            @RequestBody CreateProductRequest request) {
        Product product = productApplicationService.updateProduct(
            id, request.getName(), request.getDescription(),
            request.getPrice(), request.getStock(),
            request.getCategory(), request.getImageUrl());
        return ResponseEntity.ok(product);
    }
}