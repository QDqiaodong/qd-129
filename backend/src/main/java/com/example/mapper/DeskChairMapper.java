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

    @Select("SELECT dc.*, ra.area_name FROM desk_chair dc LEFT JOIN reading_area ra ON dc.area_id = ra.id ORDER BY dc.area_id, dc.asset_code")
    List<DeskChair> findAllWithAreaIncludeDisabled();

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

    @Select("<script>" +
            "SELECT dc.*, ra.area_name FROM desk_chair dc LEFT JOIN reading_area ra ON dc.area_id = ra.id " +
            "WHERE dc.status = 1 " +
            "<if test='areaId != null'> AND dc.area_id = #{areaId} </if>" +
            "<if test='tagIds != null and tagIds.size() > 0'> " +
            "AND dc.id IN (SELECT dct.desk_chair_id FROM desk_chair_tag dct WHERE dct.tag_id IN " +
            "<foreach collection='tagIds' item='tagId' open='(' separator=',' close=')'>#{tagId}</foreach>) " +
            "</if>" +
            "ORDER BY dc.asset_code" +
            "</script>")
    List<DeskChair> search(@Param("areaId") Long areaId, @Param("tagIds") List<Long> tagIds);
}