package com.vybe.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterForm {

    @NotBlank(message = "Укажите имя пользователя")
    @Size(min = 3, max = 32, message = "Имя пользователя: от 3 до 32 символов")
    @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Только латинские буквы, цифры, точка и _")
    private String username;

    @NotBlank(message = "Укажите email")
    @Email(message = "Некорректный email")
    @Size(max = 120)
    private String email;

    @NotBlank(message = "Укажите отображаемое имя")
    @Size(max = 60, message = "Не длиннее 60 символов")
    private String displayName;

    @NotBlank(message = "Придумайте пароль")
    @Size(min = 8, max = 72, message = "Пароль: от 8 до 72 символов")
    private String password;

    private String confirmPassword;
}
