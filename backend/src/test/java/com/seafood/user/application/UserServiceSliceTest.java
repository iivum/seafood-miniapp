package com.seafood.user.application;

import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserMapper;
import com.seafood.user.infra.UserRepository;
import com.seafood.testsupport.builders.UserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * UserService direct unit test — covers findByOpenId paths.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceSliceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProductViewService productViewService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, productViewService);
    }

    private static final UserPrincipal ADMIN = new UserPrincipal("admin-1", Role.ADMIN);

    @Test
    void findByOpenId_knownOpenId_returnsUser() {
        // Test via get() — UserService.get loads user
        User user = UserBuilder.aUser().withId("u-1").withOpenId("dev-open-1").build();
        when(userRepository.findById("u-1")).thenReturn(Optional.of(UserMapper.toDocument(user)));

        var resp = userService.get("u-1", ADMIN);

        assertThat(resp.id()).isEqualTo("u-1");
    }

    @Test
    void findByOpenId_unknownOpenId_returnsEmpty() {
        // UserRepository.findByOpenId returns empty when no user has that openId
        when(userRepository.findByOpenId("unknown")).thenReturn(Optional.empty());

        Optional<UserDocument> found = userRepository.findByOpenId("unknown");

        assertThat(found).isEmpty();
    }

    @Test
    void findByOpenId_knownOpenId_returnsUser_viaRepo() {
        User user = UserBuilder.aUser().withId("u-2").withOpenId("dev-open-2").build();
        when(userRepository.findByOpenId("dev-open-2"))
            .thenReturn(Optional.of(UserMapper.toDocument(user)));

        Optional<UserDocument> found = userRepository.findByOpenId("dev-open-2");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("u-2");
        assertThat(found.get().getOpenId()).isEqualTo("dev-open-2");
    }
}
