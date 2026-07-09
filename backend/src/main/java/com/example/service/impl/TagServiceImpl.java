package com.example.service.impl;

import com.example.entity.Tag;
import com.example.mapper.DeskChairTagMapper;
import com.example.mapper.TagMapper;
import com.example.service.TagService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TagServiceImpl implements TagService {

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private DeskChairTagMapper deskChairTagMapper;

    @Override
    public List<Tag> findAll() {
        return tagMapper.findAllWithCount();
    }

    @Override
    public Tag findById(Long id) {
        return tagMapper.selectById(id);
    }

    @Override
    public Tag findByCode(String tagCode) {
        return tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getTagCode, tagCode));
    }

    @Override
    @Transactional
    public Tag save(Tag tag) {
        tagMapper.insert(tag);
        return tag;
    }

    @Override
    @Transactional
    public Tag update(Tag tag) {
        tagMapper.updateById(tag);
        return tag;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        deskChairTagMapper.deleteTag(id, null);
        tagMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteTagFromDeskChair(Long deskChairId, Long tagId) {
        deskChairTagMapper.deleteTag(deskChairId, tagId);
    }
}