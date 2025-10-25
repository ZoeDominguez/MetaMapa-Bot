package com.metamapa.telegram.DTO;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HechoDTO(
    String titulo,
    @JsonProperty("etiquetas") List<String> etiquetas,
    String categoria,
    String ubicacion,
    LocalDate fecha,
    String origen
) {}