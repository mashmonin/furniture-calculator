package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.FramePost;

public interface FramePostRepository extends JpaRepository<FramePost, Long> {

    List<FramePost> findByFrameTypeId(Long frameTypeId);
}
