package com.example.service.impl;

import com.example.entity.ReadingArea;
import com.example.mapper.ReadingAreaMapper;
import com.example.service.ReadingAreaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReadingAreaServiceImpl implements ReadingAreaService {

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Override
    public List<ReadingArea> findAll() {
        return readingAreaMapper.findAllWithCount();
    }

    @Override
    public ReadingArea findById(Long id) {
        return readingAreaMapper.selectById(id);
    }

    @Override
    public ReadingArea findByCode(String areaCode) {
        return readingAreaMapper.selectOne(new LambdaQueryWrapper<ReadingArea>().eq(ReadingArea::getAreaCode, areaCode));
    }

    @Override
    public ReadingArea save(ReadingArea readingArea) {
        readingAreaMapper.insert(readingArea);
        return readingArea;
    }

    @Override
    public ReadingArea update(ReadingArea readingArea) {
        readingAreaMapper.updateById(readingArea);
        return readingArea;
    }

    @Override
    public void deleteById(Long id) {
        readingAreaMapper.deleteById(id);
    }
}