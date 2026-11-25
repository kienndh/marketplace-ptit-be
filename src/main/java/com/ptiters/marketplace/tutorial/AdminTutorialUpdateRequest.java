package com.ptiters.marketplace.tutorial;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminTutorialUpdateRequest {

    @JsonProperty("feature_key")
    private String featureKey;

    @Size(max = 120, message = "Tiêu đề không được vượt quá 120 ký tự")
    private String title;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;

    private boolean descriptionSet;

    @JsonProperty("action_key")
    private String actionKey;

    private boolean actionKeySet;

    @JsonProperty("is_published")
    private Boolean isPublished;

    @JsonSetter("feature_key")
    public void setFeatureKey(String featureKey) {
        this.featureKey = featureKey;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSet = true;
    }

    @JsonSetter("action_key")
    public void setActionKey(String actionKey) {
        this.actionKey = actionKey;
        this.actionKeySet = true;
    }

    @JsonSetter("is_published")
    public void setIsPublished(Boolean isPublished) {
        this.isPublished = isPublished;
    }
}
