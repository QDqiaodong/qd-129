package com.example.vo;

import lombok.Data;

@Data
public class AreaTagStatVO {

    private Long areaId;

    private Long tagId;

    private String tagName;

    private String tagColor;

    /** 当前分区内使用该标签的桌椅数量（不含已删除资产） */
    private Integer deskChairCount;
}
