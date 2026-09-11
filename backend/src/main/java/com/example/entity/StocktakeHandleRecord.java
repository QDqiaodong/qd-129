package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stocktake_handle_record")
public class StocktakeHandleRecord {

    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_CONFIRM = "CONFIRM";
    public static final String ACTION_RECHECK = "RECHECK";
    public static final String ACTION_COMPLETE = "COMPLETE";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private Long batchId;

    @TableField("item_id")
    private Long itemId;

    /** SUBMIT 录入实盘 / CONFIRM 确认差异 / RECHECK 重新复核 */
    @TableField("handle_action")
    private String action;

    @TableField("opinion")
    private String opinion;

    @TableField("operator")
    private String operator;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
