package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.AreaChangeBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AreaChangeBatchMapper extends BaseMapper<AreaChangeBatch> {

    @Select("SELECT b.*, ra.area_name AS target_area_name " +
            "FROM area_change_batch b LEFT JOIN reading_area ra ON b.target_area_id = ra.id " +
            "ORDER BY b.created_at DESC, b.id DESC")
    List<AreaChangeBatch> findAllWithArea();

    @Select("SELECT b.*, ra.area_name AS target_area_name " +
            "FROM area_change_batch b LEFT JOIN reading_area ra ON b.target_area_id = ra.id " +
            "WHERE b.id = #{id}")
    AreaChangeBatch findByIdWithArea(@Param("id") Long id);

    @Select("SELECT b.*, ra.area_name AS target_area_name " +
            "FROM area_change_batch b LEFT JOIN reading_area ra ON b.target_area_id = ra.id " +
            "WHERE b.batch_no = #{batchNo}")
    AreaChangeBatch findByBatchNoWithArea(@Param("batchNo") String batchNo);
}
