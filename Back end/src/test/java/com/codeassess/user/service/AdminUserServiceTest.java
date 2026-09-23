package com.codeassess.user.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.user.dto.response.UserResponse;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.mapper.UserMapper;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getAllUsersReturnsMappedList() {
        User user = User.builder().id("1").email("test@test.com").role(Role.STUDENT).build();
        UserResponse response = UserResponse.builder().id("1").email("test@test.com").build();

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        List<UserResponse> result = adminUserService.getAllUsers();
        
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void getUserByIdReturnsMappedUserWhenFound() {
        User user = User.builder().id("1").email("test@test.com").build();
        UserResponse response = UserResponse.builder().id("1").email("test@test.com").build();

        when(userRepository.findById("1")).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = adminUserService.getUserById("1");
        
        assertThat(result.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void getUserByIdThrowsWhenNotFound() {
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUserById("999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: 999");
    }
}
