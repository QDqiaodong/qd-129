package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.NightInspectionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NightInspectionItemMapper extends BaseMapper<NightInspectionItem> {

    @Select("SELECT i.*, ra.area_name, ra.area_code " +
            "FROM night_inspection_item i " +
            "LEFT JOIN reading_area ra ON i.area_id = ra.id " +
            "WHERE i.batch_id = #{batchId} ORDER BY i.id")
    List<NightInspectionItem> findByBatchIdWithArea(@Param("batchId") Long batchId);

    /**
     * 巡检记录列表：只取已逐件登记（CHECKED）的明细，
     * 支持按分区和是否有问题筛选，供结束后的记录查询页使用。
     */
    @Select("<script>" +
            "SELECT i.*, ra.area_name, ra.area_code, b.status AS batch_status " +
            "FROM night_inspection_item i " +
            "LEFT JOIN reading_area ra ON i.area_id = ra.id " +
            "LEFT JOIN night_inspection_batch b ON i.batch_id = b.id " +
            "<where>" +
            "i.check_status = 'CHECKED' " +
            "<if test='areaId != null'> AND i.area_id = #{areaId} </if>" +
            "<if test='hasProblem != null'> AND i.has_problem = #{hasProblem} </if>" +
            "</where>" +
            "ORDER BY i.checked_at DESC, i.id DESC" +
            "</script>")
    List<NightInspectionItem> searchRecords(@Param("areaId") Long areaId, @Param("hasProblem") Integer hasProblem);

    /** 同一件桌椅的全部巡检记录（跨批次，新的在前），点开明细可追溯到每一次夜间巡检 */
    @Select("SELECT i.*, ra.area_name, ra.area_code, b.status AS batch_status " +
            "FROM night_inspection_item i " +
            "LEFT JOIN reading_area ra ON i.area_id = ra.id " +
            "LEFT JOIN night_inspection_batch b ON i.batch_id = b.id " +
            "WHERE i.desk_chair_id = #{deskChairId} AND i.check_status = 'CHECKED' " +
            "ORDER BY i.checked_at DESC, i.id DESC")
    List<NightInspectionItem> findRecordsByDeskChair(@Param("deskChairId") Long deskChairId);

    @Select("SELECT COUNT(1) FROM night_inspection_item WHERE batch_id = #{batchId} AND check_status = 'CHECKED'")
    int countChecked(@Param("batchId") Long batchId);

    @Select("SELECT COUNT(1) FROM night_inspection_item WHERE batch_id = #{batchId} " +
            "AND check_status = 'CHECKED' AND has_problem = 1")
    int countProblem(@Param("batchId") Long batchId);
}
