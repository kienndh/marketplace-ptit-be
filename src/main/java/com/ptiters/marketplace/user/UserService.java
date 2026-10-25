package com.ptiters.marketplace.user;

import com.ptiters.marketplace.exception.ApiException;
import jakarta.persistence.EntityManager;
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
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public UserResponse getById(Long userId){
        return userRepository.findById(userId)
                .map(user -> modelMapper.map(user, UserResponse.class))
                .orElseThrow(() -> ApiException.notFound("User not exists"));
    }

    public UserResponse updateMe(Long userId, UpdateUserRequest request) {
        System.out.println("UNKNOWN FIELDS = " + request.getUnknownFields());
        if (!request.getUnknownFields().isEmpty()) {
            throw ApiException.unprocessable("Unsupported Field : " +
                    String.join(", ", request.getUnknownFields().keySet()));
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User Not Found"));
        if(request.getFullName() != null){
            user.setFullName(request.getFullName().trim());
        }
        if(request.getPhone() != null){
            user.setPhone(request.getPhone());
        }
        if(request.getBio() != null){
            user.setBio(request.getBio());
        }
        if(request.getDeliveryArea() != null){
            user.setDeliveryArea(request.getDeliveryArea());
        }
        var savedUser = userRepository.save(user);
        return modelMapper.map(savedUser, UserResponse.class);
    }
}
