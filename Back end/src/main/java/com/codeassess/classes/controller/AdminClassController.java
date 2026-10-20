package com.codeassess.classes.controller;

import com.codeassess.classes.dto.request.CreateClassDto;
import com.codeassess.classes.dto.request.EnrollStudentDto;
import com.codeassess.classes.dto.request.UpdateClassDto;
import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.dto.response.ClassStudentResponse;
import com.codeassess.classes.dto.response.ExcelImportResultDto;
import com.codeassess.classes.service.AdminClassService;
import com.codeassess.classes.service.ExcelRosterImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/classes")
@RequiredArgsConstructor
public class AdminClassController {

    private final AdminClassService adminClassService;
    private final ExcelRosterImportService excelRosterImportService;

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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/students")
    public List<ClassStudentResponse> getClassStudents(@PathVariable String id) {
        return adminClassService.getClassStudents(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/students")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassStudentResponse enrollStudent(@PathVariable String id, @RequestBody EnrollStudentDto dto) {
        return adminClassService.enrollStudent(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/students/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unenrollStudent(@PathVariable String id, @PathVariable String studentId) {
        adminClassService.unenrollStudent(id, studentId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/{id}/students/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExcelImportResultDto> importStudentsFromExcel(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "strict", defaultValue = "false") boolean strict) {
        ExcelImportResultDto result = excelRosterImportService.importStudentsFromExcel(id, file, strict);
        return ResponseEntity.ok(result);
    }
}
