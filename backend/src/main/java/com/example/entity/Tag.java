package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tag")
public class Tag {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tag_code")
    private String tagCode;

    @TableField("tag_name")
    private String tagName;

    @TableField("tag_color")
    private String tagColor;

    @TableField("description")
    private String description;

    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer deskChairCount;
}