package com.metamapa.telegram.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HechoDTO(
    Long id,
    String titulo
) {}