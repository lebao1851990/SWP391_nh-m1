package com.codeassess.classes.controller;

import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.service.AdminClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.codeassess.classes.dto.request.CreateClassDto;
import com.codeassess.classes.dto.request.UpdateClassDto;

import java.util.List;

@RestController
@RequestMapping("/api/admin/classes")
@RequiredArgsConstructor
public class AdminClassController {

    private final AdminClassService adminClassService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<ClassResponse> getAllClasses() {
        return adminClassService.getAllClasses();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ClassResponse getClassById(@PathVariable String id) {
        return adminClassService.getClassById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ClassResponse createClass(@Valid @RequestBody CreateClassDto dto) {
        return adminClassService.createClass(dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ClassResponse updateClass(@PathVariable String id, @Valid @RequestBody UpdateClassDto dto) {
        return adminClassService.updateClass(id, dto);
    }
}
