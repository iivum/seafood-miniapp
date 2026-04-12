package com.seafood.product.interfaces.rest;

import com.seafood.product.application.ProductApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImageUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductApplicationService productApplicationService;

    @Test
    void testUploadImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.jpg", 
            MediaType.IMAGE_JPEG_VALUE, 
            "test image content".getBytes()
        );

        mockMvc.perform(multipart("/products/upload")
                .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").exists());
    }

    @Test
    void testUploadImageInvalidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "", 
            MediaType.IMAGE_JPEG_VALUE, 
            "".getBytes()
        );

        mockMvc.perform(multipart("/products/upload")
                .file(file)
                .param("productId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUploadImageNoFile() throws Exception {
        mockMvc.perform(multipart("/products/upload")
                .param("productId", "1"))
                .andExpect(status().isBadRequest());
    }
}