package com.example.service.impl;

import com.example.entity.AreaChangeLog;
import com.example.mapper.AreaChangeLogMapper;
import com.example.service.AreaChangeLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AreaChangeLogServiceImpl implements AreaChangeLogService {

    @Autowired
    private AreaChangeLogMapper areaChangeLogMapper;

    @Override
    public List<AreaChangeLog> findAll() {
        List<AreaChangeLog> logs = new ArrayList<>();
        logs.addAll(areaChangeLogMapper.findAllFromTable00());
        logs.addAll(areaChangeLogMapper.findAllFromTable01());
        logs.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return logs;
    }

    @Override
    public List<AreaChangeLog> findByDeskChairId(Long deskChairId) {
        List<AreaChangeLog> logs = new ArrayList<>();
        logs.addAll(areaChangeLogMapper.findByDeskChairIdFromTable00(deskChairId));
        logs.addAll(areaChangeLogMapper.findByDeskChairIdFromTable01(deskChairId));
        logs.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return logs;
    }
}