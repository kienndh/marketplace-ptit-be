package com.ptiters.marketplace.user;

import com.ptiters.marketplace.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal){
        return userService.getById(principal.getUserId());
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody UpdateUserRequest request){
        return userService.updateMe(principal.getUserId(), request);
    }
}
