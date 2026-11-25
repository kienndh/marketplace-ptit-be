package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdate {

    @Size(min = 1, max = 100, message = "Họ tên phải từ 1 đến 100 ký tự.")
    @JsonProperty("full_name")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
    @JsonProperty("phone")
    private String phone;

    @JsonProperty("show_phone_publicly")
    private Boolean showPhonePublicly;

    @JsonProperty("social_links")
    private List<SocialLink> socialLinks;

    @Size(max = 500, message = "Bio tối đa 500 ký tự.")
    @JsonProperty("bio")
    private String bio;

    @Size(max = 255, message = "Khu vực giao hàng tối đa 255 ký tự.")
    @JsonProperty("delivery_area")
    private String deliveryArea;

    @Size(max = 255, message = "Khu vực gặp gỡ tối đa 255 ký tự.")
    @JsonProperty("default_meetup_area")
    private String defaultMeetupArea;
}
