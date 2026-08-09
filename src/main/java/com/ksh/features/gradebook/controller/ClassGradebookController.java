package com.ksh.features.gradebook.controller;

import com.ksh.entities.ClassEntity;
import com.ksh.features.classes.controller.support.ClassDetailModelSupport;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.gradebook.service.ClassGradebookService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class ClassGradebookController {
    private final ClassesService classesService;
    private final ClassDetailModelSupport detailSupport;
    private final ClassGradebookService gradebookService;

    public ClassGradebookController(ClassesService classesService,
                                    ClassDetailModelSupport detailSupport,
                                    ClassGradebookService gradebookService) {
        this.classesService = classesService;
        this.detailSupport = detailSupport;
        this.gradebookService = gradebookService;
    }

    @GetMapping("/lecturer/classes/{classId}/scores")
    public String gradebook(@PathVariable Long classId,
                            @AuthenticationPrincipal KshUserDetails user,
                            Model model) {
        ClassEntity clazz = classesService.getViewable(classId, user.getId(), user.getRole());
        detailSupport.populateDetail(model, clazz, "scores", user.getId(), user.getRole());
        model.addAttribute("gradebook", gradebookService.build(classId));
        return "classes/detail-scores";
    }
}
