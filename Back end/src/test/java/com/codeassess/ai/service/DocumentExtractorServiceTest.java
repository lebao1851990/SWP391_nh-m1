package com.codeassess.ai.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentExtractorServiceTest {

    private DocumentExtractorService documentExtractorService;

    @BeforeEach
    void setUp() {
        documentExtractorService = new DocumentExtractorService();
    }

    @Test
    @DisplayName("Extract text from valid TXT file successfully")
    void testExtractTextFromTxt_Success() {
        String content = "Chủ đề: Cấu trúc dữ liệu và giải thuật trong Java.\n" +
                "Thuật toán Dijkstra tìm đường đi ngắn nhất đồ thị có trọng số không âm.";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );

        DocumentExtractorService.ExtractedDocument doc = documentExtractorService.extractText(file);

        assertThat(doc.content()).contains("Chủ đề: Cấu trúc dữ liệu và giải thuật trong Java.");
        assertThat(doc.content()).contains("Thuật toán Dijkstra");
        assertThat(doc.filename()).isEqualTo("lecture.txt");
        assertThat(doc.extension()).isEqualTo("txt");
    }

    @Test
    @DisplayName("Reject null or empty file with ResponseStatusException")
    void testExtractText_EmptyOrNullFileRejection() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThatThrownBy(() -> documentExtractorService.extractText(emptyFile))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("trống");

        assertThatThrownBy(() -> documentExtractorService.extractText(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("trống");
    }

    @Test
    @DisplayName("Reject unsupported extension with ResponseStatusException")
    void testExtractText_UnsupportedExtension() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file",
                "payload.exe",
                "application/octet-stream",
                "MALICIOUS".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> documentExtractorService.extractText(exeFile))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không được hỗ trợ");
    }

    @Test
    @DisplayName("Reject oversized file exceeding 15MB limit")
    void testExtractText_OversizedFile() {
        byte[] oversizedData = new byte[15 * 1024 * 1024 + 10]; // 15MB + 10 bytes
        MockMultipartFile bigFile = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                oversizedData
        );

        assertThatThrownBy(() -> documentExtractorService.extractText(bigFile))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("Extract text from valid DOCX file using Apache POI")
    void testExtractTextFromDocx_Success() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("Chương 4: Cây nhị phân tìm kiếm (Binary Search Tree). Nội dung đầy đủ của chương học.");

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun r2 = p2.createRun();
            r2.setText("Phép quay AVL cân bằng cây: LL, RR, LR, RL. Thao tác cân bằng sau khi chèn nút.");

            doc.write(baos);
        }

        MockMultipartFile docxFile = new MockMultipartFile(
                "file",
                "algorithms.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                baos.toByteArray()
        );

        DocumentExtractorService.ExtractedDocument extracted = documentExtractorService.extractText(docxFile);

        assertThat(extracted.content()).contains("Cây nhị phân tìm kiếm");
        assertThat(extracted.content()).contains("Phép quay AVL");
    }

    @Test
    @DisplayName("Extract text from valid text-layer PDF using Apache PDFBox 3")
    void testExtractTextFromPdf_Success() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cos = new PDPageContentStream(document, page)) {
                cos.beginText();
                cos.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cos.newLineAtOffset(100, 700);
                cos.showText("Data Structures: Dynamic Programming Knapsack Problem with extensive theory");
                cos.endText();
            }
            document.save(baos);
        }

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "knapsack.pdf",
                "application/pdf",
                baos.toByteArray()
        );

        DocumentExtractorService.ExtractedDocument extracted = documentExtractorService.extractText(pdfFile);

        assertThat(extracted.content()).contains("Dynamic Programming Knapsack Problem");
    }

    @Test
    @DisplayName("Reject scanned PDF with no extractable text layer")
    void testExtractTextFromPdf_ScannedPdfThrowsBadRequest() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage blankPage = new PDPage();
            document.addPage(blankPage);
            document.save(baos);
        }

        MockMultipartFile scannedPdfFile = new MockMultipartFile(
                "file",
                "scanned.pdf",
                "application/pdf",
                baos.toByteArray()
        );

        assertThatThrownBy(() -> documentExtractorService.extractText(scannedPdfFile))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không có lớp văn bản");
    }

    @Test
    @DisplayName("Sanitize and normalize text with special whitespace characters")
    void testSanitizeAndNormalize() {
        String raw = "Dòng 1   nhiều  dấu cách\r\n\r\n\r\nDòng 2\u0000";
        String sanitized = documentExtractorService.sanitizeAndNormalize(raw);

        assertThat(sanitized).doesNotContain("\u0000");
        assertThat(sanitized).doesNotContain("\r");
        assertThat(sanitized).contains("Dòng 1 nhiều dấu cách");
    }
}
