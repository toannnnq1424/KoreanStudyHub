package com.ksh.features.classes.imports.service;

import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.classes.imports.parser.ExcelTemplateBuilder;
import com.ksh.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;

/** Sample identities must exist and be eligible, not fictional email addresses. */
@Service
public class ClassImportTemplateService {
    private final ClassesService classes;
    private final UserRepository users;
    private final ExcelTemplateBuilder builder;

    public ClassImportTemplateService(ClassesService classes, UserRepository users, ExcelTemplateBuilder builder) {
        this.classes = classes;
        this.users = users;
        this.builder = builder;
    }

    @Transactional(readOnly = true)
    public byte[] build(Long classId, Long actorId, Role role) throws IOException {
        classes.getOwnerManaged(classId, actorId, role);
        var candidates = users.findImportCandidates(classId, org.springframework.data.domain.PageRequest.of(0, 25));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Không có sinh viên đang hoạt động chưa thuộc lớp để tạo mẫu. Hãy tạo tài khoản sinh viên trước.");
        }
        return builder.build(candidates.stream().map(u -> new String[]{u.getEmail(), "", u.getFullName(), ""}).toList());
    }
}
