package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.NightInspectionBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NightInspectionBatchMapper extends BaseMapper<NightInspectionBatch> {

    @Select("<script>" +
            "SELECT b.*, ra.area_name, ra.area_code " +
            "FROM night_inspection_batch b LEFT JOIN reading_area ra ON b.area_id = ra.id " +
            "<where>" +
            "<if test='areaId != null'> AND b.area_id = #{areaId} </if>" +
            "<if test='status != null and status != \"\"'> AND b.status = #{status} </if>" +
            "</where>" +
            "ORDER BY b.created_at DESC, b.id DESC" +
            "</script>")
    List<NightInspectionBatch> searchWithArea(@Param("areaId") Long areaId, @Param("status") String status);

    @Select("SELECT b.*, ra.area_name, ra.area_code " +
            "FROM night_inspection_batch b LEFT JOIN reading_area ra ON b.area_id = ra.id " +
            "WHERE b.id = #{id}")
    NightInspectionBatch findByIdWithArea(@Param("id") Long id);

    @Select("SELECT b.*, ra.area_name, ra.area_code " +
            "FROM night_inspection_batch b LEFT JOIN reading_area ra ON b.area_id = ra.id " +
            "WHERE b.area_id = #{areaId} AND b.status = 'OPEN' " +
            "ORDER BY b.created_at DESC, b.id DESC")
    List<NightInspectionBatch> findOpenByArea(@Param("areaId") Long areaId);
}
