package com.example.service;

import com.example.entity.ReadingArea;

import java.util.List;

public interface ReadingAreaService {

    List<ReadingArea> findAll();

    ReadingArea findById(Long id);

    ReadingArea findByCode(String areaCode);

    ReadingArea save(ReadingArea readingArea);

    ReadingArea update(ReadingArea readingArea);

    void deleteById(Long id);
}