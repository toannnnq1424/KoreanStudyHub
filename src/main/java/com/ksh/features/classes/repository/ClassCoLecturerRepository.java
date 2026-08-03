package com.ksh.features.classes.repository;

import com.ksh.entities.ClassCoLecturer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ClassCoLecturerRepository extends JpaRepository<ClassCoLecturer, Long> {

    boolean existsByClassIdAndLecturerId(Long classId, Long lecturerId);

    List<ClassCoLecturer> findAllByClassIdIn(Collection<Long> classIds);
}
