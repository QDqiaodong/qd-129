package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.RepairOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {

    @Select("<script>" +
            "SELECT ro.*, ra.area_name, ra.area_code, dc.asset_code, dc.status AS desk_status " +
            "FROM repair_order ro " +
            "LEFT JOIN reading_area ra ON ro.area_id = ra.id " +
            "LEFT JOIN desk_chair dc ON ro.desk_chair_id = dc.id " +
            "<where>" +
            "<if test='areaId != null'> AND ro.area_id = #{areaId} </if>" +
            "<if test='status != null and status != \"\"'> AND ro.status = #{status} </if>" +
            "</where>" +
            "ORDER BY ro.created_at DESC, ro.id DESC" +
            "</script>")
    List<RepairOrder> search(@Param("areaId") Long areaId, @Param("status") String status);

    @Select("SELECT ro.*, ra.area_name, ra.area_code, dc.asset_code, dc.status AS desk_status " +
            "FROM repair_order ro " +
            "LEFT JOIN reading_area ra ON ro.area_id = ra.id " +
            "LEFT JOIN desk_chair dc ON ro.desk_chair_id = dc.id " +
            "WHERE ro.id = #{id}")
    RepairOrder findDetailById(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM repair_order " +
            "WHERE desk_chair_id = #{deskChairId} AND status IN ('PENDING', 'IN_PROGRESS')")
    int countOpenByDeskChair(@Param("deskChairId") Long deskChairId);
}
