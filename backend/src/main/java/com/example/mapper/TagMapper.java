package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    @Select("SELECT t.*, COUNT(dct.id) as desk_chair_count FROM tag t LEFT JOIN desk_chair_tag dct ON t.id = dct.tag_id WHERE t.status = 1 GROUP BY t.id ORDER BY t.tag_code")
    List<Tag> findAllWithCount();

    @Select("SELECT t.* FROM tag t INNER JOIN desk_chair_tag dct ON t.id = dct.tag_id WHERE dct.desk_chair_id = #{deskChairId}")
    List<Tag> findByDeskChairId(Long deskChairId);
}