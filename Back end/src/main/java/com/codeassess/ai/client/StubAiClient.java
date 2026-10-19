package com.codeassess.ai.client;

import com.codeassess.ai.exception.AiIntegrationException;

/**
 * Stub implementation of AiClient for offline and development environments
 * when external LLM API keys (OpenAI / Gemini) are not configured.
 * Clearly marks responses as simulated/development mock output to maintain transparency.
 */
public class StubAiClient implements AiClient {

    private static final String SIMULATION_HEADER = 
            "[CHẾ ĐỘ MÔ PHỎNG / DEV MOCK - DỊCH VỤ AI CHƯA CẤU HÌNH API KEY]\n";

    @Override
    public String generateSimilarityExplanation(String prompt) throws AiIntegrationException {
        return SIMULATION_HEADER + 
                "Hệ thống phát hiện các đoạn mã có sự tương đồng về cây cú pháp trừu tượng (AST) và k-gram fingerprints. " +
                "Do chưa cấu hình khóa API (OpenAI/Gemini), chức năng giải thích tự động đang ở chế độ mô phỏng. " +
                "Giảng viên vui lòng sử dụng trình đối chiếu trực quan mã nguồn Side-by-Side để kiểm tra chi tiết.";
    }

    @Override
    public String generateCodeReview(String prompt) throws AiIntegrationException {
        return SIMULATION_HEADER + 
                "Bằng chứng tương đồng cho thấy cấu trúc khối lệnh giữa hai bài nộp có mức độ trùng lặp đáng kể. " +
                "Khuyến nghị giảng viên đối chiếu thủ công giữa hai đoạn mã nguồn để đưa ra phán quyết chính thức.";
    }

    @Override
    public String generateStudentFeedback(String systemPrompt, String userPrompt) throws AiIntegrationException {
        return SIMULATION_HEADER + 
                "Trợ lý học tập AI hiện đang ở chế độ chờ kết nối khóa API nhà cung cấp. " +
                "Khuyến nghị học tập: Vui lòng đối chiếu mã lỗi biên dịch, kiểm tra kỹ kiểu dữ liệu, điều kiện lặp và cú pháp phương thức trong bài làm của bạn.";
    }
}
