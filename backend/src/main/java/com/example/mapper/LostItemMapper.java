package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.LostItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LostItemMapper extends BaseMapper<LostItem> {

    @Select("<script>" +
            "SELECT li.*, ra.area_name, ra.area_code, dc.status AS desk_status " +
            "FROM lost_item li " +
            "LEFT JOIN reading_area ra ON li.area_id = ra.id " +
            "LEFT JOIN desk_chair dc ON li.desk_chair_id = dc.id " +
            "<where>" +
            "<if test='areaId != null'> AND li.area_id = #{areaId} </if>" +
            "<if test='status != null and status != \"\"'> AND li.status = #{status} </if>" +
            "</where>" +
            "ORDER BY li.created_at DESC, li.id DESC" +
            "</script>")
    List<LostItem> search(@Param("areaId") Long areaId, @Param("status") String status);

    @Select("SELECT li.*, ra.area_name, ra.area_code, dc.status AS desk_status " +
            "FROM lost_item li " +
            "LEFT JOIN reading_area ra ON li.area_id = ra.id " +
            "LEFT JOIN desk_chair dc ON li.desk_chair_id = dc.id " +
            "WHERE li.id = #{id}")
    LostItem findDetailById(@Param("id") Long id);

    /**
     * 查询单件桌椅当前仍待领取的遗失物品（最新一条），
     * 高峰占座开批/追加时据此拦截并给出明确单号。
     */
    @Select("SELECT li.*, ra.area_name, ra.area_code, dc.status AS desk_status " +
            "FROM lost_item li " +
            "LEFT JOIN reading_area ra ON li.area_id = ra.id " +
            "LEFT JOIN desk_chair dc ON li.desk_chair_id = dc.id " +
            "WHERE li.desk_chair_id = #{deskChairId} AND li.status = 'PENDING' " +
            "ORDER BY li.id DESC LIMIT 1")
    LostItem findPendingByDeskChair(@Param("deskChairId") Long deskChairId);

    /** 分区内全部待领取遗失物品，供占座勾选页一次性取回做禁选标记 */
    @Select("SELECT li.*, ra.area_name, ra.area_code, dc.status AS desk_status " +
            "FROM lost_item li " +
            "LEFT JOIN reading_area ra ON li.area_id = ra.id " +
            "LEFT JOIN desk_chair dc ON li.desk_chair_id = dc.id " +
            "WHERE li.area_id = #{areaId} AND li.status = 'PENDING' " +
            "ORDER BY li.id DESC")
    List<LostItem> findPendingByAreaId(@Param("areaId") Long areaId);
}
