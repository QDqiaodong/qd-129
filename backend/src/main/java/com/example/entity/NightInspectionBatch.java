package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("night_inspection_batch")
public class NightInspectionBatch {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_no")
    private String batchNo;

    @TableField("area_id")
    private Long areaId;

    /** 应巡数量：建批时按分区在册桌椅数（含停用）固化，巡件提交不重算 */
    @TableField("total_count")
    private Integer totalCount;

    /** 已巡数量：已逐件提交灯/插座/桌面结果的明细数 */
    @TableField("checked_count")
    private Integer checkedCount;

    /** 问题数量：已巡明细中存在灯/插座/桌面异常的件数 */
    @TableField("problem_count")
    private Integer problemCount;

    @TableField("remark")
    private String remark;

    @TableField("operator")
    private String operator;

    /** OPEN（巡检中）/ COMPLETED（已结束） */
    @TableField("status")
    private String status;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String areaName;

    @TableField(exist = false)
    private String areaCode;

    @TableField(exist = false)
    private List<NightInspectionItem> items;
}
