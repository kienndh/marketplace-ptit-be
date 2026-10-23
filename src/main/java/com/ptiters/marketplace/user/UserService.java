package com.ptiters.marketplace.user;

import com.ptiters.marketplace.exception.ApiException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Getter
@AllArgsConstructor
@Service
public class UserService {
    private UserRepository userRepository;
    private ModelMapper modelMapper;

    @Transactional(readOnly = true)
    public UserResponse getById(Long userId){
        return userRepository.findById(userId)
                .map(user -> modelMapper.map(user, UserResponse.class))
                .orElseThrow(() -> ApiException.notFound("User not exists"));
    }
}
