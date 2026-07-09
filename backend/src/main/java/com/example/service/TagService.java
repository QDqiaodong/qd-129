package com.example.service;

import com.example.entity.Tag;

import java.util.List;

public interface TagService {

    List<Tag> findAll();

    Tag findById(Long id);

    Tag findByCode(String tagCode);

    Tag save(Tag tag);

    Tag update(Tag tag);

    void deleteById(Long id);

    void deleteTagFromDeskChair(Long deskChairId, Long tagId);
}