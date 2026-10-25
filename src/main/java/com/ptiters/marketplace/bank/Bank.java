package com.ptiters.marketplace.bank;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Bank {
    private String bin;
    private String code;
    @JsonProperty("short_name")
    private String shortName;
    private String name;
}

