package com.example.mapper;

import com.example.entity.AreaChangeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AreaChangeLogMapper {

    @Insert("INSERT INTO area_change_log_${tableIndex} (desk_chair_id, old_area_id, new_area_id, change_reason, operator, batch_no) VALUES (#{log.deskChairId}, #{log.oldAreaId}, #{log.newAreaId}, #{log.changeReason}, #{log.operator}, #{log.batchNo})")
    @Options(useGeneratedKeys = true, keyProperty = "log.id")
    void insertLog(@Param("log") AreaChangeLog log, @Param("tableIndex") String tableIndex);

    @Select("SELECT log.*, dc.asset_code, ra_old.area_name as old_area_name, ra_new.area_name as new_area_name " +
            "FROM area_change_log_00 log LEFT JOIN desk_chair dc ON log.desk_chair_id = dc.id " +
            "LEFT JOIN reading_area ra_old ON log.old_area_id = ra_old.id " +
            "LEFT JOIN reading_area ra_new ON log.new_area_id = ra_new.id " +
            "ORDER BY log.created_at DESC")
    List<AreaChangeLog> findAllFromTable00();

    @Select("SELECT log.*, dc.asset_code, ra_old.area_name as old_area_name, ra_new.area_name as new_area_name " +
            "FROM area_change_log_01 log LEFT JOIN desk_chair dc ON log.desk_chair_id = dc.id " +
            "LEFT JOIN reading_area ra_old ON log.old_area_id = ra_old.id " +
            "LEFT JOIN reading_area ra_new ON log.new_area_id = ra_new.id " +
            "ORDER BY log.created_at DESC")
    List<AreaChangeLog> findAllFromTable01();

    @Select("SELECT log.*, dc.asset_code, ra_old.area_name as old_area_name, ra_new.area_name as new_area_name " +
            "FROM area_change_log_00 log LEFT JOIN desk_chair dc ON log.desk_chair_id = dc.id " +
            "LEFT JOIN reading_area ra_old ON log.old_area_id = ra_old.id " +
            "LEFT JOIN reading_area ra_new ON log.new_area_id = ra_new.id " +
            "WHERE log.desk_chair_id = #{deskChairId} ORDER BY log.created_at DESC")
    List<AreaChangeLog> findByDeskChairIdFromTable00(@Param("deskChairId") Long deskChairId);

    @Select("SELECT log.*, dc.asset_code, ra_old.area_name as old_area_name, ra_new.area_name as new_area_name " +
            "FROM area_change_log_01 log LEFT JOIN desk_chair dc ON log.desk_chair_id = dc.id " +
            "LEFT JOIN reading_area ra_old ON log.old_area_id = ra_old.id " +
            "LEFT JOIN reading_area ra_new ON log.new_area_id = ra_new.id " +
            "WHERE log.desk_chair_id = #{deskChairId} ORDER BY log.created_at DESC")
    List<AreaChangeLog> findByDeskChairIdFromTable01(@Param("deskChairId") Long deskChairId);

    @Select("SELECT log.*, dc.asset_code, ra_old.area_name as old_area_name, ra_new.area_name as new_area_name " +
            "FROM area_change_log_00 log LEFT JOIN desk_chair dc ON log.desk_chair_id = dc.id " +
            "LEFT JOIN reading_area ra_old ON log.old_area_id = ra_old.id " +
            "LEFT JOIN reading_area ra_new ON log.new_area_id = ra_new.id " +
            "WHERE log.batch_no = #{batchNo} ORDER BY log.id")
    List<AreaChangeLog> findByBatchNoFromTable00(@Param("batchNo") String batchNo);

    @Select("SELECT log.*, dc.asset_code, ra_old.area_name as old_area_name, ra_new.area_name as new_area_name " +
            "FROM area_change_log_01 log LEFT JOIN desk_chair dc ON log.desk_chair_id = dc.id " +
            "LEFT JOIN reading_area ra_old ON log.old_area_id = ra_old.id " +
            "LEFT JOIN reading_area ra_new ON log.new_area_id = ra_new.id " +
            "WHERE log.batch_no = #{batchNo} ORDER BY log.id")
    List<AreaChangeLog> findByBatchNoFromTable01(@Param("batchNo") String batchNo);

    @Select("SELECT log.*, dc.asset_code, ra_old.area_name as old_area_name, ra_new.area_name as new_area_name " +
            "FROM area_change_log_00 log INNER JOIN desk_chair dc ON log.desk_chair_id = dc.id AND dc.status >= 0 " +
            "LEFT JOIN reading_area ra_old ON log.old_area_id = ra_old.id " +
            "LEFT JOIN reading_area ra_new ON log.new_area_id = ra_new.id " +
            "WHERE log.old_area_id = #{areaId} OR log.new_area_id = #{areaId} " +
            "ORDER BY log.created_at DESC LIMIT #{limit}")
    List<AreaChangeLog> findByAreaIdFromTable00(@Param("areaId") Long areaId, @Param("limit") int limit);

    @Select("SELECT log.*, dc.asset_code, ra_old.area_name as old_area_name, ra_new.area_name as new_area_name " +
            "FROM area_change_log_01 log INNER JOIN desk_chair dc ON log.desk_chair_id = dc.id AND dc.status >= 0 " +
            "LEFT JOIN reading_area ra_old ON log.old_area_id = ra_old.id " +
            "LEFT JOIN reading_area ra_new ON log.new_area_id = ra_new.id " +
            "WHERE log.old_area_id = #{areaId} OR log.new_area_id = #{areaId} " +
            "ORDER BY log.created_at DESC LIMIT #{limit}")
    List<AreaChangeLog> findByAreaIdFromTable01(@Param("areaId") Long areaId, @Param("limit") int limit);
}