package com.paperpilot.dto;

import lombok.Data;

import java.util.List;

@Data
public class PaperDTO {

    private String doi;
    private String title;
    private List<String> authors;
    private String abstracts;
    private String journal;
    private Integer publishYear;
    private String quartile;
    private Integer citations;
    private String sourceUrl;
}
