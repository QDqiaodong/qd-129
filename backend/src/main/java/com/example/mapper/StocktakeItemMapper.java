package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.StocktakeItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StocktakeItemMapper extends BaseMapper<StocktakeItem> {

    @Select("SELECT i.*, ra_book.area_name AS book_area_name, ra_act.area_name AS actual_area_name " +
            "FROM stocktake_item i " +
            "LEFT JOIN reading_area ra_book ON i.book_area_id = ra_book.id " +
            "LEFT JOIN reading_area ra_act ON i.actual_area_id = ra_act.id " +
            "WHERE i.batch_id = #{batchId} ORDER BY i.id")
    List<StocktakeItem> findByBatchIdWithArea(@Param("batchId") Long batchId);

    @Delete("DELETE FROM stocktake_item WHERE batch_id = #{batchId}")
    int deleteByBatchId(@Param("batchId") Long batchId);

    @Select("SELECT COUNT(1) FROM stocktake_item WHERE batch_id = #{batchId} AND check_status = 'CONFIRMED'")
    int countConfirmed(@Param("batchId") Long batchId);
}
