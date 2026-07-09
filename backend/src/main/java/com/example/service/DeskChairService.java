package com.example.service;

import com.example.entity.DeskChair;

import java.util.List;

public interface DeskChairService {

    List<DeskChair> findAll();

    List<DeskChair> findByAreaId(Long areaId);

    List<DeskChair> findByTagId(Long tagId);

    List<DeskChair> findByTagIds(List<Long> tagIds);

    DeskChair findById(Long id);

    DeskChair findByAssetCode(String assetCode);

    DeskChair save(DeskChair deskChair);

    DeskChair update(DeskChair deskChair);

    void deleteById(Long id);

    void bindTags(Long deskChairId, List<Long> tagIds);

    void updateArea(Long deskChairId, Long newAreaId, String changeReason, String operator);

    List<DeskChair> getStandardDimensions();
}