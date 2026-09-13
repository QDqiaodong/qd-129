package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.entity.ReadingArea;
import com.example.mapper.ReadingAreaMapper;
import com.example.service.ReadingAreaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    @Override
    public ReadingArea markClosed(Long id, LocalDateTime closedUntil) {
        if (id == null) {
            throw new IllegalArgumentException("缺少阅览分区");
        }
        if (closedUntil == null) {
            throw new IllegalArgumentException("请选择闭馆结束时刻");
        }
        if (!closedUntil.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("闭馆结束时刻必须晚于当前时间");
        }
        ReadingArea area = readingAreaMapper.selectById(id);
        if (area == null) {
            throw new IllegalArgumentException("阅览分区不存在");
        }
        // 显式 set 闭馆结束时刻；普通编辑分区资料时不带该字段，互不影响
        readingAreaMapper.update(null, new LambdaUpdateWrapper<ReadingArea>()
                .eq(ReadingArea::getId, id)
                .set(ReadingArea::getClosedUntil, closedUntil)
                .set(ReadingArea::getUpdatedAt, LocalDateTime.now()));
        return readingAreaMapper.selectById(id);
    }

    @Override
    public ReadingArea clearClosed(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("缺少阅览分区");
        }
        ReadingArea area = readingAreaMapper.selectById(id);
        if (area == null) {
            throw new IllegalArgumentException("阅览分区不存在");
        }
        // 清空闭馆结束时刻必须显式 set，NOT_NULL 更新策略会跳过 null
        readingAreaMapper.update(null, new LambdaUpdateWrapper<ReadingArea>()
                .eq(ReadingArea::getId, id)
                .set(ReadingArea::getClosedUntil, null)
                .set(ReadingArea::getUpdatedAt, LocalDateTime.now()));
        return readingAreaMapper.selectById(id);
    }
}
