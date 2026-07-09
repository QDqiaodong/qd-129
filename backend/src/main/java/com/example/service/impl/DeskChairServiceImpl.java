package com.example.service.impl;

import com.example.entity.AreaChangeLog;
import com.example.entity.DeskChair;
import com.example.entity.Tag;
import com.example.mapper.AreaChangeLogMapper;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.DeskChairTagMapper;
import com.example.mapper.TagMapper;
import com.example.service.DeskChairService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DeskChairServiceImpl implements DeskChairService {

    private static final String REDIS_KEY_DIMENSIONS = "desk_chair:standard_dimensions";

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private AreaChangeLogMapper areaChangeLogMapper;

    @Autowired
    private DeskChairTagMapper deskChairTagMapper;

    @Autowired
    @Qualifier("objectRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<DeskChair> findAll() {
        List<DeskChair> list = deskChairMapper.findAllWithArea();
        list.forEach(this::loadTags);
        return list;
    }

    @Override
    public List<DeskChair> findByAreaId(Long areaId) {
        List<DeskChair> list = deskChairMapper.findByAreaId(areaId);
        list.forEach(this::loadTags);
        return list;
    }

    @Override
    public List<DeskChair> findByTagId(Long tagId) {
        List<DeskChair> list = deskChairMapper.findByTagId(tagId);
        list.forEach(this::loadTags);
        return list;
    }

    @Override
    public List<DeskChair> findByTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return findAll();
        }
        String tagIdsStr = tagIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        List<DeskChair> list = deskChairMapper.findByTagIds(tagIdsStr);
        list.forEach(this::loadTags);
        return list;
    }

    @Override
    public DeskChair findById(Long id) {
        DeskChair deskChair = deskChairMapper.selectById(id);
        loadTags(deskChair);
        return deskChair;
    }

    @Override
    public DeskChair findByAssetCode(String assetCode) {
        DeskChair deskChair = deskChairMapper.findByAssetCode(assetCode);
        loadTags(deskChair);
        return deskChair;
    }

    @Override
    @Transactional
    public DeskChair save(DeskChair deskChair) {
        deskChairMapper.insert(deskChair);
        if (deskChair.getTags() != null && !deskChair.getTags().isEmpty()) {
            List<Long> tagIds = deskChair.getTags().stream().map(Tag::getId).toList();
            bindTags(deskChair.getId(), tagIds);
        }
        return deskChair;
    }

    @Override
    @Transactional
    public DeskChair update(DeskChair deskChair) {
        deskChairMapper.updateById(deskChair);
        if (deskChair.getTags() != null) {
            List<Long> tagIds = deskChair.getTags().stream().map(Tag::getId).toList();
            bindTags(deskChair.getId(), tagIds);
        }
        return deskChair;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        deskChairMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void bindTags(Long deskChairId, List<Long> tagIds) {
        deskChairTagMapper.deleteByDeskChairId(deskChairId);
        
        if (tagIds != null && !tagIds.isEmpty()) {
            tagIds.forEach(tagId -> {
                deskChairTagMapper.insertTag(deskChairId, tagId);
            });
        }
    }

    @Override
    @Transactional
    public void updateArea(Long deskChairId, Long newAreaId, String changeReason, String operator) {
        DeskChair deskChair = deskChairMapper.selectById(deskChairId);
        if (deskChair == null) {
            throw new RuntimeException("桌椅不存在");
        }

        Long oldAreaId = deskChair.getAreaId();
        deskChair.setAreaId(newAreaId);
        deskChairMapper.updateById(deskChair);

        AreaChangeLog log = new AreaChangeLog();
        log.setDeskChairId(deskChairId);
        log.setOldAreaId(oldAreaId);
        log.setNewAreaId(newAreaId);
        log.setChangeReason(changeReason);
        log.setOperator(operator);

        String tableIndex = deskChairId % 2 == 0 ? "00" : "01";
        areaChangeLogMapper.insertLog(log, tableIndex);
    }

    @Override
    public List<DeskChair> getStandardDimensions() {
        try {
            List<Object> cachedList = redisTemplate.opsForList().range(REDIS_KEY_DIMENSIONS, 0, -1);
            if (cachedList != null && !cachedList.isEmpty()) {
                return cachedList.stream()
                        .map(obj -> (DeskChair) obj)
                        .toList();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<DeskChair> list = deskChairMapper.selectList(new LambdaQueryWrapper<DeskChair>()
                .select(DeskChair::getDimensions)
                .isNotNull(DeskChair::getDimensions)
                .groupBy(DeskChair::getDimensions));

        try {
            if (!list.isEmpty()) {
                redisTemplate.delete(REDIS_KEY_DIMENSIONS);
                redisTemplate.opsForList().leftPushAll(REDIS_KEY_DIMENSIONS, list.toArray());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    private void loadTags(DeskChair deskChair) {
        if (deskChair != null) {
            deskChair.setTags(tagMapper.findByDeskChairId(deskChair.getId()));
        }
    }
}