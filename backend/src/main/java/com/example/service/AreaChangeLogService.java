package com.example.service;

import com.example.entity.AreaChangeLog;

import java.util.List;

public interface AreaChangeLogService {

    List<AreaChangeLog> findAll();

    List<AreaChangeLog> findByDeskChairId(Long deskChairId);
}