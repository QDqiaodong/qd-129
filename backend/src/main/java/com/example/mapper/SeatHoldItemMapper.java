package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.SeatHoldItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeatHoldItemMapper extends BaseMapper<SeatHoldItem> {

    @Select("SELECT i.*, ra.area_name, dc.status AS desk_status " +
            "FROM seat_hold_item i " +
            "LEFT JOIN reading_area ra ON i.area_id = ra.id " +
            "LEFT JOIN desk_chair dc ON i.desk_chair_id = dc.id " +
            "WHERE i.batch_id = #{batchId} ORDER BY i.id")
    List<SeatHoldItem> findByBatchId(@Param("batchId") Long batchId);

    @Select("SELECT i.*, ra.area_name, dc.status AS desk_status " +
            "FROM seat_hold_item i " +
            "LEFT JOIN seat_hold_batch b ON i.batch_id = b.id " +
            "LEFT JOIN reading_area ra ON i.area_id = ra.id " +
            "LEFT JOIN desk_chair dc ON i.desk_chair_id = dc.id " +
            "WHERE i.desk_chair_id = #{deskChairId} AND i.item_status IN ('HOLDING', 'TIMEOUT') " +
            "ORDER BY i.id DESC")
    List<SeatHoldItem> findActiveByDeskChair(@Param("deskChairId") Long deskChairId);

    @Select("<script>" +
            "SELECT i.*, b.status AS batch_status, b.area_id AS batch_area_id, " +
            "ra.area_name, ra.area_code, dc.status AS desk_status " +
            "FROM seat_hold_item i " +
            "INNER JOIN seat_hold_batch b ON i.batch_id = b.id " +
            "LEFT JOIN reading_area ra ON b.area_id = ra.id " +
            "LEFT JOIN desk_chair dc ON i.desk_chair_id = dc.id " +
            "WHERE b.status = 'OPEN' AND i.item_status IN ('HOLDING', 'TIMEOUT') " +
            "<if test='areaId != null'> AND b.area_id = #{areaId} </if>" +
            "ORDER BY i.id DESC" +
            "</script>")
    List<SeatHoldItem> findOpenHolds(@Param("areaId") Long areaId);
}
