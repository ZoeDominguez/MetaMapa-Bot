package com.metamapa.telegram.DTO;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HechoDTO(
    String nombreColeccion,
    String titulo,
    String etiqueta,
    String categoria,
    String ubicacion,
    LocalDate fecha,
    String origen
) {}