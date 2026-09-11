package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("stocktake_item")
public class StocktakeItem {

    public static final String DIFF_MATCH = "MATCH";
    public static final String DIFF_MISSING = "MISSING";
    public static final String DIFF_SURPLUS = "SURPLUS";
    public static final String DIFF_WRONG_AREA = "WRONG_AREA";
    public static final String DIFF_STATUS_MISMATCH = "STATUS_MISMATCH";
    public static final String DIFF_TAG_MISMATCH = "TAG_MISMATCH";

    public static final String CHECK_PENDING = "PENDING";
    public static final String CHECK_CONFIRMED = "CONFIRMED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("desk_chair_id")
    private Long deskChairId;

    @TableField("asset_code")
    private String assetCode;

    /**
     * 差异类型：
     * MATCH 一致 / MISSING 缺失（在册未盘到）/ SURPLUS 盘盈（盘到但在册不存在）/
     * WRONG_AREA 错区 / STATUS_MISMATCH 停用不符 / TAG_MISMATCH 标签不符
     */
    @TableField("diff_type")
    private String diffType;

    @TableField("diff_detail")
    private String diffDetail;

    // —— 在册快照（应盘）——
    @TableField("book_area_id")
    private Long bookAreaId;

    @TableField("book_status")
    private Integer bookStatus;

    /** 在册标签 ID 有序列表，逗号拼接 */
    @TableField("book_tag_ids")
    private String bookTagIds;

    /** 在册标签名称快照，逗号拼接，仅展示用 */
    @TableField("book_tag_names")
    private String bookTagNames;

    // —— 实盘录入 ——
    @TableField("actual_area_id")
    private Long actualAreaId;

    @TableField("actual_status")
    private Integer actualStatus;

    /** 实盘标签 ID 有序列表，逗号拼接 */
    @TableField("actual_tag_ids")
    private String actualTagIds;

    @TableField("actual_tag_names")
    private String actualTagNames;

    /** PENDING 待核 / CONFIRMED 已确认 */
    @TableField("check_status")
    private String checkStatus;

    @TableField("handle_opinion")
    private String handleOpinion;

    @TableField("confirmed_by")
    private String confirmedBy;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;

    /** 复核次数：每次“重新复核”加一 */
    @TableField("recheck_count")
    private Integer recheckCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String bookAreaName;

    @TableField(exist = false)
    private String actualAreaName;

    @TableField(exist = false)
    private List<StocktakeHandleRecord> records;
}
