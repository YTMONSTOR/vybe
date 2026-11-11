package com.vybe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileForm {

    @NotBlank(message = "Имя не может быть пустым")
    @Size(max = 60, message = "Не длиннее 60 символов")
    private String displayName;

    @Size(max = 1000, message = "Описание не длиннее 1000 символов")
    private String bio;
}
