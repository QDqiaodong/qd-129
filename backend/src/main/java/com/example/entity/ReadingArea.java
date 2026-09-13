package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reading_area")
public class ReadingArea {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("area_code")
    private String areaCode;

    @TableField("area_name")
    private String areaName;

    @TableField("description")
    private String description;

    @TableField("status")
    private Integer status;

    /**
     * 今日闭馆结束时刻：非空且晚于当前时间表示该区正在闭馆；
     * 到期后该字段仍保留历史值，但不再产生闭馆效力，同分区可再开高峰占座
     */
    @TableField("closed_until")
    private LocalDateTime closedUntil;

    /**
     * 今晚临时加座名额：正数表示临时增加的容纳人数，配合 extraSeatUntil 使用。
     * 失效时刻过后该字段仍保留历史值，但不再产生加座效力（看板、开批均按档案容量算）
     */
    @TableField("extra_seat_count")
    private Integer extraSeatCount;

    /**
     * 临时加座失效时刻：非空且晚于当前时间时，extraSeatCount 笔临时名额仍然有效；
     * 到期后字段保留，同分区不再享受临时名额，容量看板自动回到档案容量
     */
    @TableField("extra_seat_until")
    private LocalDateTime extraSeatUntil;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer deskChairCount;

    /** 临时加座是否仍在有效期：失效时刻晚于当前时刻 */
    public boolean isExtraSeatActive() {
        return isExtraSeatActive(LocalDateTime.now());
    }

    /** 注入当前时刻的判定，供服务层与测试统一口径 */
    public boolean isExtraSeatActive(LocalDateTime now) {
        return extraSeatCount != null && extraSeatCount > 0
                && extraSeatUntil != null && extraSeatUntil.isAfter(now);
    }

    /** 当前仍生效的临时加座名额；已到期或未挂牌返回 0 */
    public int activeExtraSeatCount() {
        return activeExtraSeatCount(LocalDateTime.now());
    }

    /** 注入当前时刻的有效名额，供服务层与测试统一口径 */
    public int activeExtraSeatCount(LocalDateTime now) {
        return isExtraSeatActive(now) ? extraSeatCount : 0;
    }
}