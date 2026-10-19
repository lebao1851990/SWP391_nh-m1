package com.codeassess.classes.service;

import com.codeassess.classes.dto.response.ExcelImportResultDto;
import com.codeassess.classes.dto.response.ExcelRowErrorDto;
import com.codeassess.classes.entity.ClassStudent;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ExcelBatchImportException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ExcelRosterImportService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final CourseClassRepository courseClassRepository;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;
    private final PlatformTransactionManager transactionManager;

    public ExcelRosterImportService(
            CourseClassRepository courseClassRepository,
            UserRepository userRepository,
            ClassStudentRepository classStudentRepository,
            PlatformTransactionManager transactionManager) {
        this.courseClassRepository = courseClassRepository;
        this.userRepository = userRepository;
        this.classStudentRepository = classStudentRepository;
        this.transactionManager = transactionManager;
    }

    private static class ParsedRow {
        int rowNumber;
        String email;
        String studentId;
        User resolvedStudent;
    }

    public ExcelImportResultDto importStudentsFromExcel(String classId, MultipartFile file, boolean strict) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file must not be empty");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Excel file size exceeds the 10MB limit");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new IllegalArgumentException("File must be an Excel spreadsheet (.xlsx or .xls)");
        }

        CourseClass courseClass = courseClassRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found with id: " + classId));

        List<ParsedRow> candidateRows = new ArrayList<>();
        List<ExcelRowErrorDto> errors = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();

        // 1. Parse Excel in memory
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1) {
                return ExcelImportResultDto.builder()
                        .totalRows(0)
                        .importedCount(0)
                        .failedCount(0)
                        .isStrict(strict)
                        .errors(Collections.emptyList())
                        .build();
            }

            int emailCol = -1;
            int studentIdCol = -1;
            int nameCol = -1;

            // Inspect Header Row (Row 0)
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String headerVal = getCellStringValue(cell).toLowerCase().trim();
                    if (headerVal.contains("email") || headerVal.contains("mail")) {
                        emailCol = cell.getColumnIndex();
                    } else if (headerVal.contains("name")) {
                        nameCol = cell.getColumnIndex();
                    } else if (headerVal.contains("code") || headerVal.contains("id") || headerVal.contains("student")) {
                        studentIdCol = cell.getColumnIndex();
                    }
                }
            }

            if (emailCol == -1 && studentIdCol == -1) {
                // Fallback default: Column 0 is email, Column 1 is studentId
                emailCol = 0;
                studentIdCol = 1;
            }

            int lastRowNum = sheet.getLastRowNum();
            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                String email = emailCol >= 0 ? getCellStringValue(row.getCell(emailCol)).trim() : "";
                String studentId = studentIdCol >= 0 ? getCellStringValue(row.getCell(studentIdCol)).trim() : "";

                // If both are completely blank, skip empty row
                if (email.isBlank() && studentId.isBlank()) {
                    continue;
                }

                ParsedRow candidate = new ParsedRow();
                candidate.rowNumber = r + 1; // 1-indexed for user-friendly error reporting
                candidate.email = email;
                candidate.studentId = studentId;

                // Validate Row Syntax
                String fileKey = (!email.isBlank() ? email.toLowerCase() : studentId.toLowerCase());
                if (seenInFile.contains(fileKey)) {
                    errors.add(ExcelRowErrorDto.builder()
                            .rowNumber(candidate.rowNumber)
                            .identifier(!email.isBlank() ? email : studentId)
                            .errorCode("DUPLICATE_IN_FILE")
                            .message("Duplicate student entry within the same Excel sheet")
                            .build());
                    continue;
                }
                seenInFile.add(fileKey);

                if (!email.isBlank() && !EMAIL_PATTERN.matcher(email).matches()) {
                    errors.add(ExcelRowErrorDto.builder()
                            .rowNumber(candidate.rowNumber)
                            .identifier(email)
                            .errorCode("INVALID_EMAIL")
                            .message("Invalid email format")
                            .build());
                    continue;
                }

                // Resolve User
                Optional<User> userOpt = Optional.empty();
                if (!studentId.isBlank()) {
                    userOpt = userRepository.findById(studentId);
                }
                if (userOpt.isEmpty() && !email.isBlank()) {
                    userOpt = userRepository.findByEmail(email);
                }

                if (userOpt.isEmpty()) {
                    errors.add(ExcelRowErrorDto.builder()
                            .rowNumber(candidate.rowNumber)
                            .identifier(!email.isBlank() ? email : studentId)
                            .errorCode("STUDENT_NOT_FOUND")
                            .message("No registered user account found with provided email or ID")
                            .build());
                    continue;
                }

                User student = userOpt.get();
                if (student.getRole() != Role.STUDENT) {
                    errors.add(ExcelRowErrorDto.builder()
                            .rowNumber(candidate.rowNumber)
                            .identifier(!email.isBlank() ? email : studentId)
                            .errorCode("NOT_A_STUDENT")
                            .message("User exists but does not have the STUDENT role (role: " + student.getRole() + ")")
                            .build());
                    continue;
                }

                // Check existing DB enrollment
                if (classStudentRepository.existsByClassIdAndStudentId(classId, student.getId())) {
                    errors.add(ExcelRowErrorDto.builder()
                            .rowNumber(candidate.rowNumber)
                            .identifier(!email.isBlank() ? email : studentId)
                            .errorCode("ALREADY_ENROLLED")
                            .message("Student is already enrolled in this class")
                            .build());
                    continue;
                }

                candidate.resolvedStudent = student;
                candidateRows.add(candidate);
            }

        } catch (Exception e) {
            log.error("Failed to parse Excel file for class {}: {}", classId, e.getMessage(), e);
            throw new IllegalArgumentException("Failed to read Excel workbook: " + e.getMessage(), e);
        }

        int totalEvaluated = candidateRows.size() + errors.size();

        // 2. Strict Mode Execution
        if (strict) {
            if (!errors.isEmpty()) {
                ExcelImportResultDto failedResult = ExcelImportResultDto.builder()
                        .totalRows(totalEvaluated)
                        .importedCount(0)
                        .failedCount(errors.size())
                        .isStrict(true)
                        .errors(errors)
                        .build();
                throw new ExcelBatchImportException(
                        "Strict import failed with " + errors.size() + " validation errors. 0 rows inserted.",
                        failedResult);
            }

            // All candidates valid: execute in a single atomic transaction
            TransactionTemplate atomicTx = new TransactionTemplate(transactionManager);
            atomicTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

            try {
                atomicTx.executeWithoutResult(status -> {
                    for (ParsedRow row : candidateRows) {
                        ClassStudent.ClassStudentId csId = new ClassStudent.ClassStudentId(courseClass.getId(), row.resolvedStudent.getId());
                        ClassStudent cs = ClassStudent.builder()
                                .id(csId)
                                .courseClass(courseClass)
                                .student(row.resolvedStudent)
                                .build();
                        classStudentRepository.save(cs);
                    }
                });

                return ExcelImportResultDto.builder()
                        .totalRows(candidateRows.size())
                        .importedCount(candidateRows.size())
                        .failedCount(0)
                        .isStrict(true)
                        .errors(Collections.emptyList())
                        .build();

            } catch (Exception e) {
                log.error("Database constraint or concurrency failure during strict import: {}", e.getMessage(), e);
                ExcelRowErrorDto dbError = ExcelRowErrorDto.builder()
                        .rowNumber(0)
                        .identifier("BATCH_TRANSACTION")
                        .errorCode(e instanceof DataIntegrityViolationException ? "CONCURRENT_CONFLICT" : "DATABASE_ERROR")
                        .message("Transaction rolled back due to error: " + e.getMessage())
                        .build();

                ExcelImportResultDto failedResult = ExcelImportResultDto.builder()
                        .totalRows(totalEvaluated)
                        .importedCount(0)
                        .failedCount(candidateRows.size())
                        .isStrict(true)
                        .errors(List.of(dbError))
                        .build();

                throw new ExcelBatchImportException("Strict import rolled back due to database error: " + e.getMessage(), failedResult);
            }
        }

        // 3. Partial Mode Execution
        int importedCount = 0;
        TransactionTemplate isolatedTx = new TransactionTemplate(transactionManager);
        isolatedTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (ParsedRow row : candidateRows) {
            try {
                isolatedTx.executeWithoutResult(status -> {
                    ClassStudent.ClassStudentId csId = new ClassStudent.ClassStudentId(courseClass.getId(), row.resolvedStudent.getId());
                    ClassStudent cs = ClassStudent.builder()
                            .id(csId)
                            .courseClass(courseClass)
                            .student(row.resolvedStudent)
                            .build();
                    classStudentRepository.save(cs);
                });
                importedCount++;
            } catch (DataIntegrityViolationException dive) {
                log.warn("Concurrent duplicate enrollment detected for student {} in class {}", row.resolvedStudent.getId(), classId);
                errors.add(ExcelRowErrorDto.builder()
                        .rowNumber(row.rowNumber)
                        .identifier(!row.email.isBlank() ? row.email : row.studentId)
                        .errorCode("ALREADY_ENROLLED")
                        .message("Concurrent enrollment conflict: Student already enrolled in this class")
                        .build());
            } catch (Exception ex) {
                log.error("Failed to enroll student {} in class {}: {}", row.resolvedStudent.getId(), classId, ex.getMessage());
                errors.add(ExcelRowErrorDto.builder()
                        .rowNumber(row.rowNumber)
                        .identifier(!row.email.isBlank() ? row.email : row.studentId)
                        .errorCode("INSERT_FAILED")
                        .message("Database error: " + ex.getMessage())
                        .build());
            }
        }

        // Sort errors by rowNumber for clean predictable display
        errors.sort(Comparator.comparingInt(ExcelRowErrorDto::getRowNumber));

        return ExcelImportResultDto.builder()
                .totalRows(totalEvaluated)
                .importedCount(importedCount)
                .failedCount(errors.size())
                .isStrict(false)
                .errors(errors)
                .build();
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    yield String.format("%.0f", num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }
}
