package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.StocktakeHandleRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StocktakeHandleRecordMapper extends BaseMapper<StocktakeHandleRecord> {

    // handle_action 通过别名映射到实体字段 action（@TableField 不作用于原生 @Select 的结果映射）
    @Select("SELECT *, handle_action AS action FROM stocktake_handle_record " +
            "WHERE batch_id = #{batchId} ORDER BY created_at ASC, id ASC")
    List<StocktakeHandleRecord> findByBatchId(@Param("batchId") Long batchId);

    @Select("SELECT *, handle_action AS action FROM stocktake_handle_record " +
            "WHERE item_id = #{itemId} ORDER BY created_at ASC, id ASC")
    List<StocktakeHandleRecord> findByItemId(@Param("itemId") Long itemId);

    @Delete("DELETE FROM stocktake_handle_record WHERE batch_id = #{batchId} " +
            "AND item_id IS NOT NULL AND handle_action IN ('CONFIRM', 'RECHECK')")
    int deleteItemRecordsByBatchId(@Param("batchId") Long batchId);
}
