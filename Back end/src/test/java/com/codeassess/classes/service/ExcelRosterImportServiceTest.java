package com.codeassess.classes.service;

import com.codeassess.classes.dto.response.ExcelImportResultDto;
import com.codeassess.classes.entity.ClassStudent;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ExcelBatchImportException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcelRosterImportServiceTest {

    @Mock
    private CourseClassRepository courseClassRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ExcelRosterImportService importService;

    private CourseClass sampleClass;
    private User student1;
    private User student2;
    private User student3;

    @BeforeEach
    void setUp() {
        importService = new ExcelRosterImportService(
                courseClassRepository,
                userRepository,
                classStudentRepository,
                transactionManager
        );

        sampleClass = CourseClass.builder()
                .id("class-1")
                .name("Software Architecture")
                .code("SWP391")
                .build();

        student1 = User.builder()
                .id("std-1")
                .email("student1@fpt.edu.vn")
                .fullName("Nguyen Van A")
                .role(Role.STUDENT)
                .build();

        student2 = User.builder()
                .id("std-2")
                .email("student2@fpt.edu.vn")
                .fullName("Le Thi B")
                .role(Role.STUDENT)
                .build();

        student3 = User.builder()
                .id("std-3")
                .email("student3@fpt.edu.vn")
                .fullName("Tran Van C")
                .role(Role.STUDENT)
                .build();

        lenient().when(courseClassRepository.findById("class-1")).thenReturn(Optional.of(sampleClass));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private byte[] createExcelWorkbook(String[][] data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Students");
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < data[r].length; c++) {
                    row.createCell(c).setCellValue(data[r][c]);
                }
            }
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    // --- 1. Valid File Import ---
    @Test
    @DisplayName("1. Valid Excel file imports 100% of rows successfully")
    void validExcel_ImportsAllRowsSuccessfully() throws Exception {
        String[][] data = {
                {"Email", "StudentId"},
                {"student1@fpt.edu.vn", "std-1"},
                {"student2@fpt.edu.vn", "std-2"}
        };
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelWorkbook(data));

        when(userRepository.findById("std-1")).thenReturn(Optional.of(student1));
        when(userRepository.findById("std-2")).thenReturn(Optional.of(student2));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-1")).thenReturn(false);
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-2")).thenReturn(false);

        ExcelImportResultDto result = importService.importStudentsFromExcel("class-1", file, false);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(result.getErrors()).isEmpty();
        verify(classStudentRepository, times(2)).save(any(ClassStudent.class));
    }

    // --- 2. Partial Mode: Valid Saved, Invalid Logged Without Rollback ---
    @Test
    @DisplayName("2. Partial Mode: Valid saved, invalid rows logged with exact error codes, no UnexpectedRollbackException")
    void partialMode_ValidSaved_InvalidReportedIndividually() throws Exception {
        String[][] data = {
                {"Email", "StudentId"},
                {"student1@fpt.edu.vn", "std-1"}, // Valid
                {"invalid-email-format", "std-x"}, // Invalid email
                {"student3@fpt.edu.vn", "std-3"}, // Already enrolled
                {"ghost@fpt.edu.vn", "ghost-id"}  // Not found
        };
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelWorkbook(data));

        when(userRepository.findById("std-1")).thenReturn(Optional.of(student1));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-1")).thenReturn(false);

        when(userRepository.findById("std-3")).thenReturn(Optional.of(student3));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-3")).thenReturn(true); // already enrolled

        when(userRepository.findById("ghost-id")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost@fpt.edu.vn")).thenReturn(Optional.empty());

        ExcelImportResultDto result = importService.importStudentsFromExcel("class-1", file, false);

        assertThat(result.getTotalRows()).isEqualTo(4);
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(3);
        assertThat(result.getErrors()).hasSize(3);

        assertThat(result.getErrors().get(0).getErrorCode()).isEqualTo("INVALID_EMAIL");
        assertThat(result.getErrors().get(1).getErrorCode()).isEqualTo("ALREADY_ENROLLED");
        assertThat(result.getErrors().get(2).getErrorCode()).isEqualTo("STUDENT_NOT_FOUND");

        verify(classStudentRepository, times(1)).save(any(ClassStudent.class));
    }

    // --- 3. Strict Mode: Single Validation Error Aborts Entire Batch ---
    @Test
    @DisplayName("3. Strict Mode: Single validation error aborts batch, throws ExcelBatchImportException with 0 rows added")
    void strictMode_SingleValidationError_AbortsBatch() throws Exception {
        String[][] data = {
                {"Email", "StudentId"},
                {"student1@fpt.edu.vn", "std-1"},
                {"student2@fpt.edu.vn", "std-2"},
                {"invalid-email", "std-bad"}
        };
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelWorkbook(data));

        when(userRepository.findById("std-1")).thenReturn(Optional.of(student1));
        when(userRepository.findById("std-2")).thenReturn(Optional.of(student2));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-1")).thenReturn(false);
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-2")).thenReturn(false);

        ExcelBatchImportException ex = assertThrows(ExcelBatchImportException.class, () ->
                importService.importStudentsFromExcel("class-1", file, true));

        assertThat(ex.getResult().getImportedCount()).isEqualTo(0);
        assertThat(ex.getResult().getFailedCount()).isEqualTo(1);
        assertThat(ex.getResult().getErrors().get(0).getErrorCode()).isEqualTo("INVALID_EMAIL");
        verify(classStudentRepository, never()).save(any(ClassStudent.class));
    }

    // --- 4. Strict Mode: Database Exception Mid-Batch Rolls Back Everything ---
    @Test
    @DisplayName("4. Strict Mode: Database exception mid-batch rolls back entire batch")
    void strictMode_DatabaseErrorMidBatch_TriggersCompleteRollback() throws Exception {
        String[][] data = {
                {"Email", "StudentId"},
                {"student1@fpt.edu.vn", "std-1"},
                {"student2@fpt.edu.vn", "std-2"}
        };
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelWorkbook(data));

        when(userRepository.findById("std-1")).thenReturn(Optional.of(student1));
        when(userRepository.findById("std-2")).thenReturn(Optional.of(student2));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-1")).thenReturn(false);
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-2")).thenReturn(false);

        // Simulate DB failure on the second insert
        when(classStudentRepository.save(any(ClassStudent.class)))
                .thenReturn(mock(ClassStudent.class))
                .thenThrow(new DataIntegrityViolationException("Simulated mid-batch DB deadlock or lock timeout"));

        ExcelBatchImportException ex = assertThrows(ExcelBatchImportException.class, () ->
                importService.importStudentsFromExcel("class-1", file, true));

        assertThat(ex.getResult().getImportedCount()).isEqualTo(0);
        assertThat(ex.getResult().getErrors().get(0).getErrorCode()).isEqualTo("CONCURRENT_CONFLICT");
    }

    // --- 5. Intra-File Duplicates Handled ---
    @Test
    @DisplayName("5. Duplicate rows within the same file are detected and rejected")
    void intraFileDuplicates_DetectedAndRejected() throws Exception {
        String[][] data = {
                {"Email", "StudentId"},
                {"student1@fpt.edu.vn", "std-1"},
                {"student1@fpt.edu.vn", "std-1"} // exact duplicate row
        };
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelWorkbook(data));

        when(userRepository.findById("std-1")).thenReturn(Optional.of(student1));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-1")).thenReturn(false);

        ExcelImportResultDto result = importService.importStudentsFromExcel("class-1", file, false);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrorCode()).isEqualTo("DUPLICATE_IN_FILE");
        verify(classStudentRepository, times(1)).save(any(ClassStudent.class));
    }

    // --- 6. Role Check: Non-STUDENT user rejected ---
    @Test
    @DisplayName("6. Non-STUDENT user in Excel sheet is rejected with NOT_A_STUDENT")
    void nonStudentUser_RejectedWithNotAStudent() throws Exception {
        User teacher = User.builder()
                .id("teacher-1")
                .email("teacher@fpt.edu.vn")
                .role(Role.TEACHER)
                .build();

        String[][] data = {
                {"Email", "StudentId"},
                {"teacher@fpt.edu.vn", "teacher-1"}
        };
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelWorkbook(data));

        when(userRepository.findById("teacher-1")).thenReturn(Optional.of(teacher));

        ExcelImportResultDto result = importService.importStudentsFromExcel("class-1", file, false);

        assertThat(result.getImportedCount()).isEqualTo(0);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrorCode()).isEqualTo("NOT_A_STUDENT");
        verify(classStudentRepository, never()).save(any(ClassStudent.class));
    }

    // --- 7. Concurrent Collision Protection in Partial Mode ---
    @Test
    @DisplayName("7. Concurrent collision during partial insert is caught at row boundary without failing batch")
    void concurrentCollision_CaughtAtRowBoundary_PreservesOtherRows() throws Exception {
        String[][] data = {
                {"Email", "StudentId"},
                {"student1@fpt.edu.vn", "std-1"},
                {"student2@fpt.edu.vn", "std-2"}
        };
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelWorkbook(data));

        when(userRepository.findById("std-1")).thenReturn(Optional.of(student1));
        when(userRepository.findById("std-2")).thenReturn(Optional.of(student2));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-1")).thenReturn(false);
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-2")).thenReturn(false);

        // Student 1 succeeds; Student 2 encounters a concurrent insert collision from another admin
        when(classStudentRepository.save(argThat(cs -> cs != null && "std-1".equals(cs.getStudent().getId()))))
                .thenReturn(mock(ClassStudent.class));
        when(classStudentRepository.save(argThat(cs -> cs != null && "std-2".equals(cs.getStudent().getId()))))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry 'class-1-std-2' for key 'PRIMARY'"));

        ExcelImportResultDto result = importService.importStudentsFromExcel("class-1", file, false);

        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrorCode()).isEqualTo("ALREADY_ENROLLED");
        assertThat(result.getErrors().get(0).getMessage()).contains("Concurrent enrollment conflict");
    }

    // --- 8. Class Not Found ---
    @Test
    @DisplayName("8. Non-existent class throws ResourceNotFoundException")
    void nonExistentClass_ThrowsResourceNotFoundException() throws Exception {
        when(courseClassRepository.findById("missing-class")).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createExcelWorkbook(new String[][]{{"Email"}}));

        assertThrows(ResourceNotFoundException.class, () ->
                importService.importStudentsFromExcel("missing-class", file, false));
    }

    // --- 9. Header Mapping with StudentCode, FullName, Email ---
    @Test
    @DisplayName("9. Header with StudentCode, FullName, and Email correctly maps columns without confusing FullName for StudentCode")
    void testImport_HeaderMappingWithFullNameAndStudentCode_ResolvesCorrectly() throws Exception {
        byte[] bytes = createExcelWorkbook(new String[][]{
                {"StudentCode", "FullName", "Email"},
                {"std-1", "Nguyen Van A", "student1@fpt.edu.vn"},
                {"std-2", "Le Thi B", "student2@fpt.edu.vn"}
        });
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        when(userRepository.findById("std-1")).thenReturn(Optional.of(student1));
        when(userRepository.findById("std-2")).thenReturn(Optional.of(student2));
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-1")).thenReturn(false);
        when(classStudentRepository.existsByClassIdAndStudentId("class-1", "std-2")).thenReturn(false);

        ExcelImportResultDto result = importService.importStudentsFromExcel("class-1", file, false);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
        verify(classStudentRepository, times(2)).save(any(ClassStudent.class));
    }

    // --- 10. File Size Limit ---
    @Test
    @DisplayName("10. Excel file exceeding 10MB limit throws IllegalArgumentException")
    void testImport_OversizedFile_ThrowsIllegalArgumentException() {
        byte[] largeContent = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "large_roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", largeContent);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                importService.importStudentsFromExcel("class-1", file, false));

        assertThat(ex.getMessage()).contains("10MB limit");
    }

    // --- 11. Empty Sheet / Only Header ---
    @Test
    @DisplayName("11. Excel file with only header row returns 0 total rows and 0 errors")
    void testImport_HeaderOnlyWorkbook_ReturnsZeroRows() throws Exception {
        byte[] bytes = createExcelWorkbook(new String[][]{
                {"Email", "StudentCode"}
        });
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ExcelImportResultDto result = importService.importStudentsFromExcel("class-1", file, false);

        assertThat(result.getTotalRows()).isEqualTo(0);
        assertThat(result.getImportedCount()).isEqualTo(0);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(result.getErrors()).isEmpty();
    }
}
