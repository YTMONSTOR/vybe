package com.vybe.dto;

import com.vybe.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VideoForm {

    @NotBlank(message = "Укажите название")
    @Size(max = 120, message = "Название не длиннее 120 символов")
    private String title;

    @Size(max = 5000, message = "Описание не длиннее 5000 символов")
    private String description;

    private Long categoryId;

    @Size(max = 400)
    private String tags;

    @NotNull
    private Visibility visibility = Visibility.PUBLIC;

    private boolean commentsEnabled = true;

    /** Duration reported by the browser, used when ffprobe is not available. */
    private Integer duration;
}
