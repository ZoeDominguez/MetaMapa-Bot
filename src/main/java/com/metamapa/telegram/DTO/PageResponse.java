package com.metamapa.telegram.DTO;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PageResponse<T>(
        List<T> content,
        int number,     
        int totalPages, 
        long totalElements,
        boolean first,
        boolean last
) {}
