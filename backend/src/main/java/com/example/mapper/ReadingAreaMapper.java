package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.ReadingArea;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReadingAreaMapper extends BaseMapper<ReadingArea> {

    @Select("SELECT ra.*, COUNT(dc.id) as desk_chair_count " +
            "FROM reading_area ra LEFT JOIN desk_chair dc ON ra.id = dc.area_id " +
            "WHERE ra.status = 1 GROUP BY ra.id ORDER BY ra.area_code")
    List<ReadingArea> findAllWithCount();
}