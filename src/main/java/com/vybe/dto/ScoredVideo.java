package com.vybe.dto;

import com.vybe.entity.Video;

/** A recommended video together with a human readable reason why it was chosen. */
public record ScoredVideo(Video video, String reason, double score) {
}
