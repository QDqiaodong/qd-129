package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("stocktake_batch")
public class StocktakeBatch {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_no")
    private String batchNo;

    @TableField("area_id")
    private Long areaId;

    /** 应盘数量：建批时按盘点分区内在册桌椅数（含停用）固化，提交实盘不重算，列表/详情/比对共用 */
    @TableField("expected_count")
    private Integer expectedCount;

    /** 实盘数量：本次录入/导入的唯一资产条数 */
    @TableField("actual_count")
    private Integer actualCount;

    /** 已核数量：逐项确认通过的明细数 */
    @TableField("checked_count")
    private Integer checkedCount;

    /** 差异数量：非 MATCH 明细数 */
    @TableField("diff_count")
    private Integer diffCount;

    @TableField("remark")
    private String remark;

    @TableField("operator")
    private String operator;

    /** OPEN（盘点中）/ COMPLETED（已完成） */
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

    /** 批次级处理记录：录入实盘、批次完成（item_id 为空的记录） */
    @TableField(exist = false)
    private List<StocktakeHandleRecord> records;

    @TableField(exist = false)
    private List<StocktakeItem> items;
}
