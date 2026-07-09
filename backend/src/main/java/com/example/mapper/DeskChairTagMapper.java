package com.example.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeskChairTagMapper {

    @Insert("INSERT INTO desk_chair_tag (desk_chair_id, tag_id) VALUES (#{deskChairId}, #{tagId})")
    void insertTag(@Param("deskChairId") Long deskChairId, @Param("tagId") Long tagId);

    @Delete("DELETE FROM desk_chair_tag WHERE desk_chair_id = #{deskChairId}")
    void deleteByDeskChairId(@Param("deskChairId") Long deskChairId);

    @Delete("DELETE FROM desk_chair_tag WHERE desk_chair_id = #{deskChairId} AND tag_id = #{tagId}")
    void deleteTag(@Param("deskChairId") Long deskChairId, @Param("tagId") Long tagId);
}