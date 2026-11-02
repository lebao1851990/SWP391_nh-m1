package com.codeassess.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Robust, memory-conscious, and secure document extraction service for AI Exam Generation.
 * Supports PDF (digital text), DOCX (Word), and TXT formats.
 * Enforces strict MIME/extension whitelisting, size limits, and sanitizes untrusted input.
 */
@Slf4j
@Service
public class DocumentExtractorService {

    public static final long MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024; // 15 MB
    public static final int MAX_EXTRACTED_CHARS = 60_000; // Cap to stay comfortably within LLM context window

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");

    public record ExtractedDocument(
            String filename,
            String extension,
            long fileSize,
            int characterCount,
            String content,
            String previewSnippet
    ) {}

    /**
     * Validates and extracts readable text from an uploaded document.
     */
    public ExtractedDocument extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp tải lên không được để trống.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    String.format("Dung lượng tệp (%.2f MB) vượt quá giới hạn tối đa cho phép là 15 MB.",
                            (double) file.getSize() / (1024 * 1024)));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên tệp không hợp lệ.");
        }

        String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Định dạng tệp không được hỗ trợ. Hệ thống chỉ chấp nhận tài liệu dạng PDF, DOCX hoặc TXT.");
        }

        String extractedText;
        try (InputStream inputStream = file.getInputStream()) {
            extractedText = switch (extension) {
                case "pdf" -> extractFromPdf(inputStream, originalFilename);
                case "docx" -> extractFromDocx(inputStream, originalFilename);
                case "txt" -> extractFromTxt(inputStream, originalFilename);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng không hỗ trợ: " + extension);
            };
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Failed to extract content from file '{}': {}", originalFilename, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không thể đọc hoặc giải mã nội dung tài liệu. Tệp có thể bị hỏng hoặc có định dạng không chuẩn.");
        }

        // Clean & Normalize Text
        extractedText = sanitizeAndNormalize(extractedText);

        if (extractedText.isBlank() || extractedText.length() < 30) {
            if ("pdf".equals(extension)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tài liệu PDF không có lớp văn bản số (có thể là tệp scan ảnh/chụp scan). Hiện hệ thống chỉ hỗ trợ PDF chứa văn bản ký tự.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nội dung văn bản trong tài liệu quá ngắn hoặc rỗng, không đủ dữ liệu để AI phân tích và sinh đề bài.");
        }

        // Bound to maximum context characters
        if (extractedText.length() > MAX_EXTRACTED_CHARS) {
            log.info("Document '{}' character count ({}) exceeds maximum limit ({}). Truncating safely.",
                    originalFilename, extractedText.length(), MAX_EXTRACTED_CHARS);
            extractedText = extractedText.substring(0, MAX_EXTRACTED_CHARS) + "\n\n[...Nội dung tài liệu được cắt ngắn để tối ưu ngữ cảnh mô hình AI...]";
        }

        String preview = extractedText.length() > 250
                ? extractedText.substring(0, 250).replace("\n", " ").trim() + "..."
                : extractedText.replace("\n", " ").trim();

        return new ExtractedDocument(
                originalFilename,
                extension,
                file.getSize(),
                extractedText.length(),
                extractedText,
                preview
        );
    }

    private String extractFromPdf(InputStream inputStream, String filename) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            if (document.isEncrypted()) {
                AccessPermission ap = document.getCurrentAccessPermission();
                if (!ap.canExtractContent()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Tệp PDF được bảo vệ bằng mật khẩu hoặc bị khóa quyền trích xuất nội dung.");
                }
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text != null ? text : "";
        }
    }

    private String extractFromDocx(InputStream inputStream, String filename) throws Exception {
        try (XWPFDocument docx = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(docx)) {
            return extractor.getText();
        }
    }

    private String extractFromTxt(InputStream inputStream, String filename) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    String sanitizeAndNormalize(String raw) {
        if (raw == null) return "";
        // Replace null characters, zero-width characters, normalize CRLF to LF
        return raw.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex >= 0 && dotIndex < filename.length() - 1)
                ? filename.substring(dotIndex + 1)
                : "";
    }
}
