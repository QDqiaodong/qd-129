package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("night_inspection_item")
public class NightInspectionItem {

    public static final String RESULT_NORMAL = "NORMAL";
    public static final String RESULT_ABNORMAL = "ABNORMAL";

    public static final String CHECK_PENDING = "PENDING";
    public static final String CHECK_CHECKED = "CHECKED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("desk_chair_id")
    private Long deskChairId;

    /** 资产编号快照 */
    @TableField("asset_code")
    private String assetCode;

    /** 巡检时所属分区快照，记录列表按分区筛选用 */
    @TableField("area_id")
    private Long areaId;

    /** 灯巡检结果：NORMAL 正常 / ABNORMAL 不亮，空 = 未登记 */
    @TableField("light_result")
    private String lightResult;

    /** 插座巡检结果：NORMAL 正常 / ABNORMAL 失灵，空 = 未登记 */
    @TableField("socket_result")
    private String socketResult;

    /** 桌面巡检结果：NORMAL 正常 / ABNORMAL 涂鸦破损，空 = 未登记 */
    @TableField("desk_surface_result")
    private String deskSurfaceResult;

    /** 问题补充描述 */
    @TableField("problem_detail")
    private String problemDetail;

    /** 是否有问题：灯/插座/桌面任一项异常即为 1 */
    @TableField("has_problem")
    private Integer hasProblem;

    /** 处理意见：有问题时必填 */
    @TableField("handle_opinion")
    private String handleOpinion;

    /** PENDING 待巡 / CHECKED 已巡 */
    @TableField("check_status")
    private String checkStatus;

    @TableField("checked_by")
    private String checkedBy;

    @TableField("checked_at")
    private LocalDateTime checkedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String areaName;

    @TableField(exist = false)
    private String areaCode;

    /** 所属批次状态（OPEN/COMPLETED），巡检记录列表展示用 */
    @TableField(exist = false)
    private String batchStatus;
}
