package com.paperpilot.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private List<T> records;
    private Long total;
    private Integer page;
    private Integer size;

    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer size) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        return result;
    }
}
