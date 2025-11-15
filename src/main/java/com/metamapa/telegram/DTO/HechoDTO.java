package com.metamapa.telegram.DTO;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record HechoDTO(
    String titulo,
    String descripcion,
    List<String> etiquetas,
    String categoria,
    String ubicacion,
    LocalDate fecha,
    String origen
) {}