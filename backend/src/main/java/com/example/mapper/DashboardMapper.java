package com.example.mapper;

import com.example.vo.AreaCapacityStatVO;
import com.example.vo.AreaTagStatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DashboardMapper {

    /**
     * 按分区统计桌椅总数、可用/占座占用/停用数量、可用与占用的容纳人数。
     * 仅统计未删除资产（desk_chair.status &gt;= 0），空分区统计结果为 0；
     * 占座占用按进行中批次（OPEN）的在占/超时未到明细判定，与档案停用、报修停用分列。
     */
    List<AreaCapacityStatVO> findAreaCapacityStats();

    /**
     * 统计单个分区内的标签构成，仅统计未删除桌椅（status &gt;= 0）关联的有效标签。
     */
    List<AreaTagStatVO> findTagStatsByAreaId(@Param("areaId") Long areaId);

    /**
     * 一次性统计所有分区的标签构成（结果带 areaId，由服务层分组），
     * 仅统计未删除桌椅关联的有效标签。
     */
    List<AreaTagStatVO> findAllTagStats();

    /**
     * 查询两个日志分片表中指定时间区间内的调区变更时间点，
     * 通过 INNER JOIN desk_chair 排除已删除资产（status &lt; 0）产生的记录。
     * areaId 为空时统计所有分区，否则统计迁入或迁出该分区的变更。
     */
    List<LocalDateTime> findChangeTimesFromTable00(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end,
                                                   @Param("areaId") Long areaId);

    List<LocalDateTime> findChangeTimesFromTable01(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end,
                                                   @Param("areaId") Long areaId);
}
