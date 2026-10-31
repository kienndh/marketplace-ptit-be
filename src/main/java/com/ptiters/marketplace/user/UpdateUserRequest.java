package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class UpdateUserRequest {
    @JsonProperty("full_name")
    @Size(max = 100, message = "full_name length must be at most 100 characters")
    @Pattern(regexp = ".*\\S.*", message = "full_name must not be blank")
    private String fullName;

    @Size(max = 20, message = "phone_number must be at most 20 characters")
    private String phone;

    @Size(max = 500, message = "bio must be at most 500 characters")
    private String bio;

    @JsonProperty("delivery_area")
    @Size(max = 255, message = "delivery_area must be at most 255 characters")
    private String deliveryArea;

    // Gom mọi trường lạ client gửi lên (avatar_url, email, role, status...) để từ chối
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private Map<String, Object> unknownFields = new HashMap<>();

    @JsonAnySetter
    public void addUnkownField(String key, Object value){
        unknownFields.put(key, value);
    }
}
