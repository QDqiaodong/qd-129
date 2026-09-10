package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.AreaChangeBatchItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AreaChangeBatchItemMapper extends BaseMapper<AreaChangeBatchItem> {

    @Select("SELECT i.*, ra_old.area_name AS old_area_name, ra_new.area_name AS new_area_name " +
            "FROM area_change_batch_item i " +
            "LEFT JOIN reading_area ra_old ON i.old_area_id = ra_old.id " +
            "LEFT JOIN reading_area ra_new ON i.new_area_id = ra_new.id " +
            "WHERE i.batch_id = #{batchId} ORDER BY i.id")
    List<AreaChangeBatchItem> findByBatchIdWithArea(@Param("batchId") Long batchId);
}
