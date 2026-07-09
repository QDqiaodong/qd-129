package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.DeskChair;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeskChairMapper extends BaseMapper<DeskChair> {

    @Select("SELECT dc.*, ra.area_name FROM desk_chair dc LEFT JOIN reading_area ra ON dc.area_id = ra.id WHERE dc.status = 1")
    List<DeskChair> findAllWithArea();

    @Select("SELECT dc.*, ra.area_name FROM desk_chair dc LEFT JOIN reading_area ra ON dc.area_id = ra.id WHERE dc.area_id = #{areaId} AND dc.status = 1")
    List<DeskChair> findByAreaId(@Param("areaId") Long areaId);

    @Select("SELECT dc.*, ra.area_name FROM desk_chair dc LEFT JOIN reading_area ra ON dc.area_id = ra.id " +
            "INNER JOIN desk_chair_tag dct ON dc.id = dct.desk_chair_id " +
            "WHERE dct.tag_id = #{tagId} AND dc.status = 1")
    List<DeskChair> findByTagId(@Param("tagId") Long tagId);

    @Select("SELECT dc.*, ra.area_name FROM desk_chair dc LEFT JOIN reading_area ra ON dc.area_id = ra.id " +
            "INNER JOIN desk_chair_tag dct ON dc.id = dct.desk_chair_id " +
            "WHERE dct.tag_id IN (${tagIds}) AND dc.status = 1")
    List<DeskChair> findByTagIds(@Param("tagIds") String tagIds);

    @Select("SELECT dc.*, ra.area_name FROM desk_chair dc LEFT JOIN reading_area ra ON dc.area_id = ra.id WHERE dc.asset_code = #{assetCode}")
    DeskChair findByAssetCode(@Param("assetCode") String assetCode);
}