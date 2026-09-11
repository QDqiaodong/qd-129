package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.StocktakeBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StocktakeBatchMapper extends BaseMapper<StocktakeBatch> {

    @Select("SELECT b.*, ra.area_name, ra.area_code " +
            "FROM stocktake_batch b LEFT JOIN reading_area ra ON b.area_id = ra.id " +
            "ORDER BY b.created_at DESC, b.id DESC")
    List<StocktakeBatch> findAllWithArea();

    @Select("SELECT b.*, ra.area_name, ra.area_code " +
            "FROM stocktake_batch b LEFT JOIN reading_area ra ON b.area_id = ra.id " +
            "WHERE b.id = #{id}")
    StocktakeBatch findByIdWithArea(@Param("id") Long id);

    @Select("SELECT b.*, ra.area_name, ra.area_code " +
            "FROM stocktake_batch b LEFT JOIN reading_area ra ON b.area_id = ra.id " +
            "WHERE b.area_id = #{areaId} AND b.status = 'OPEN' " +
            "ORDER BY b.created_at DESC, b.id DESC")
    List<StocktakeBatch> findOpenByArea(@Param("areaId") Long areaId);
}
