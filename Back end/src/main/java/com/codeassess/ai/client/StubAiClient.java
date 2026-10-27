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

    @Override
    @SuppressWarnings("unchecked")
    public <T> T generateStructuredOutput(String systemPrompt, String userPrompt, Class<T> targetClass) throws AiIntegrationException {
        if (com.codeassess.ai.dto.SemanticReviewResponse.class.equals(targetClass)) {
            com.codeassess.ai.dto.SemanticReviewResponse response = com.codeassess.ai.dto.SemanticReviewResponse.builder()
                    .summary(SIMULATION_HEADER + "Mã nguồn triển khai cấu trúc cơ bản hợp lệ. Phát hiện 2 vị trí cần tối ưu Clean Code.")
                    .cleanCodeScore(java.math.BigDecimal.valueOf(8.50))
                    .issues(java.util.List.of(
                            com.codeassess.ai.dto.CodeReviewIssueDto.builder()
                                    .line(10)
                                    .severity(com.codeassess.ai.dto.ReviewIssueSeverity.INFO)
                                    .category(com.codeassess.ai.dto.ReviewIssueCategory.READABILITY_AND_NAMING)
                                    .message("Tên biến 'x' hoặc 'tmp' chưa thể hiện rõ mục đích nghiệp vụ.")
                                    .suggestion("Nên đổi tên biến thành danh từ mang ý nghĩa đại diện như 'studentCount' hoặc 'processedIndex'.")
                                    .build(),
                            com.codeassess.ai.dto.CodeReviewIssueDto.builder()
                                    .line(null)
                                    .severity(com.codeassess.ai.dto.ReviewIssueSeverity.WARNING)
                                    .category(com.codeassess.ai.dto.ReviewIssueCategory.COMPLEXITY_AND_MAINTAINABILITY)
                                    .message("Vòng lặp lồng nhau có thể làm tăng độ phức tạp thời gian khi dữ liệu đầu vào lớn.")
                                    .suggestion("Cân nhắc sử dụng cấu trúc dữ liệu HashMap hoặc Set để giảm độ phức tạp xuống O(N).")
                                    .build()
                    ))
                    .build();
            return (T) response;
        }

        if (com.codeassess.ai.dto.AiExamGenerateResponse.class.equals(targetClass)) {
            com.codeassess.ai.dto.AiExamGenerateResponse response = com.codeassess.ai.dto.AiExamGenerateResponse.builder()
                    .title("Thuật toán Xử lý Chuỗi & Mảng (Mô phỏng AI)")
                    .problemStatement("Cho mảng số nguyên A gồm N phần tử. Hãy tìm độ dài chuỗi con tăng dần dài nhất và in ra các phần tử của chuỗi con đó.")
                    .inputSpecification("Dòng đầu tiên chứa số nguyên N (1 <= N <= 10^5). Dòng thứ hai chứa N số nguyên cách nhau bởi dấu cách.")
                    .outputSpecification("In ra độ dài chuỗi con tăng dần dài nhất.")
                    .constraints("Thời gian chạy tối đa: 1000ms. Bộ nhớ tối đa: 256MB.")
                    .sampleInput("5\n3 10 2 1 20")
                    .sampleOutput("3")
                    .testCases(java.util.List.of(
                            com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                    .name("Test cơ bản")
                                    .description("Kiểm tra mảng số nhỏ")
                                    .inputData("5\n3 10 2 1 20")
                                    .expectedOutput("3")
                                    .points(java.math.BigDecimal.valueOf(5.00))
                                    .isHidden(false)
                                    .build(),
                            com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                    .name("Test mảng đã sắp xếp")
                                    .description("Toàn bộ mảng đã tăng dần")
                                    .inputData("4\n1 2 3 4")
                                    .expectedOutput("4")
                                    .points(java.math.BigDecimal.valueOf(5.00))
                                    .isHidden(true)
                                    .build()
                    ))
                    .rubricCriteria(java.util.List.of(
                            com.codeassess.ai.dto.AiRubricItemDto.builder()
                                    .name("Tính đúng đắn thuật toán")
                                    .description("Vượt qua các trường hợp kiểm thử mẫu và kiểm thử ẩn")
                                    .maxScore(java.math.BigDecimal.valueOf(6.00))
                                    .build(),
                            com.codeassess.ai.dto.AiRubricItemDto.builder()
                                    .name("Tối ưu độ phức tạp & Clean Code")
                                    .description("Thời gian O(N log N) hoặc O(N^2), cấu trúc code rõ ràng, đặt tên biến chuẩn")
                                    .maxScore(java.math.BigDecimal.valueOf(4.00))
                                    .build()
                    ))
                    .totalRubricScore(java.math.BigDecimal.valueOf(10.00))
                    .build();
            return (T) response;
        }

        if (com.codeassess.ai.dto.DocumentAnalysisResponse.class.equals(targetClass)) {
            String promptLower = (userPrompt != null) ? userPrompt.toLowerCase(java.util.Locale.ROOT) : "";
            boolean isArrayTopic = promptLower.contains("mảng") || promptLower.contains("mang") ||
                    promptLower.contains("array") || promptLower.contains("chẵn") || promptLower.contains("chan") ||
                    promptLower.contains("even") || promptLower.contains("số nguyên") || promptLower.contains("tổng");

            boolean isStringTopic = promptLower.contains("chuỗi") || promptLower.contains("chuoi") ||
                    promptLower.contains("string") || promptLower.contains("palindrome") || promptLower.contains("đối xứng");

            if (isArrayTopic) {
                return (T) com.codeassess.ai.dto.DocumentAnalysisResponse.builder()
                        .documentName("Tai_lieu_test_AI_tu_dong_sinh_de_lap_trinh.docx")
                        .documentSizeBytes(35000)
                        .characterCount(3500)
                        .documentPreview("Xử lý mảng số nguyên trong Java. Yêu cầu tính tổng các số chẵn trong mảng một chiều...")
                        .mainTopic("Xử lý mảng số nguyên trong Java: Tính tổng các số chẵn")
                        .subTopics(java.util.List.of("Mảng một chiều (1D Array)", "Duyệt phần tử mảng bằng vòng lặp", "Toán tử chia dư kiểm tra số chẵn", "Tích lũy tổng và tránh tràn số"))
                        .keyConcepts(java.util.List.of("int[] Array", "Modulo Operator (%)", "For Loop / For-Each", "Long Accumulator", "Edge Cases"))
                        .extractedSnippets(java.util.List.of(
                                "Mảng một chiều là cấu trúc dữ liệu lưu trữ tập hợp các phần tử cùng kiểu dữ liệu liên tiếp trong bộ nhớ.",
                                "Để tính tổng các phần tử chẵn, ta duyệt từng phần tử và kiểm tra điều kiện a[i] % 2 == 0."
                        ))
                        .suggestedDifficulty("EASY")
                        .suggestedProblemTypes(java.util.List.of("Tính tổng có điều kiện", "Lọc phần tử mảng", "Tìm giá trị thỏa mãn"))
                        .isSimulation(true)
                        .build();
            } else if (isStringTopic) {
                return (T) com.codeassess.ai.dto.DocumentAnalysisResponse.builder()
                        .documentName("Tai_lieu_chuoi_ky_tu.docx")
                        .documentSizeBytes(28000)
                        .characterCount(2600)
                        .documentPreview("Xử lý chuỗi ký tự trong Java: Các phương thức String, duyệt hai con trỏ và kiểm tra chuỗi đối xứng...")
                        .mainTopic("Xử lý chuỗi ký tự trong Java: Kiểm tra Palindrome")
                        .subTopics(java.util.List.of("Lớp String & StringBuilder", "Kỹ thuật Hai con trỏ (Two Pointers)", "Kiểm tra chuỗi đối xứng"))
                        .keyConcepts(java.util.List.of("charAt()", "Two Pointers", "Palindrome", "Character Manipulation"))
                        .extractedSnippets(java.util.List.of(
                                "Một chuỗi được gọi là Palindrome nếu đọc xuôi hay ngược đều giống nhau.",
                                "Sử dụng hai con trỏ từ hai đầu chuỗi để so sánh từng cặp ký tự đối xứng."
                        ))
                        .suggestedDifficulty("EASY")
                        .suggestedProblemTypes(java.util.List.of("Xử lý chuỗi đối xứng", "Chuẩn hóa văn bản", "Đếm tần suất ký tự"))
                        .isSimulation(true)
                        .build();
            } else {
                return (T) com.codeassess.ai.dto.DocumentAnalysisResponse.builder()
                        .documentName("Tài liệu bài giảng (Mô phỏng AI)")
                        .documentSizeBytes(45000)
                        .characterCount(8500)
                        .documentPreview("Cấu trúc dữ liệu & Giải thuật nâng cao: Cây nhị phân tìm kiếm, Đồ thị và Quy hoạch động...")
                        .mainTopic("Cấu trúc dữ liệu và Giải thuật (Data Structures & Algorithms)")
                        .subTopics(java.util.List.of("Đồ thị (Graph)", "Tìm kiếm theo chiều rộng (BFS)", "Tìm kiếm theo chiều sâu (DFS)", "Đường đi ngắn nhất"))
                        .keyConcepts(java.util.List.of("Queue", "Adjacency List", "Breadth-First Search", "Shortest Path", "Visited State"))
                        .extractedSnippets(java.util.List.of(
                                "Thuật toán BFS sử dụng hàng đợi (Queue) để duyệt đồ thị theo từng tầng mức khoảng cách.",
                                "Mỗi đỉnh được đánh dấu đã ghé thăm (visited) để tránh lặp vô tận trên chu trình đồ thị."
                        ))
                        .suggestedDifficulty("MEDIUM")
                        .suggestedProblemTypes(java.util.List.of("Tìm đường đi ngắn nhất", "Duyệt thành phần liên thông", "Mô phỏng mạng lưới"))
                        .isSimulation(true)
                        .build();
            }
        }

        if (com.codeassess.ai.dto.AiDocumentExamGenerateResponse.class.equals(targetClass)) {
            String promptLower = (userPrompt != null) ? userPrompt.toLowerCase(java.util.Locale.ROOT) : "";
            boolean isArrayTopic = promptLower.contains("mảng") || promptLower.contains("mang") ||
                    promptLower.contains("array") || promptLower.contains("chẵn") || promptLower.contains("chan") ||
                    promptLower.contains("even") || promptLower.contains("số nguyên") || promptLower.contains("tổng");

            boolean isStringTopic = promptLower.contains("chuỗi") || promptLower.contains("chuoi") ||
                    promptLower.contains("string") || promptLower.contains("palindrome") || promptLower.contains("đối xứng");

            if (isArrayTopic) {
                com.codeassess.ai.dto.AiDocumentExamGenerateResponse response = com.codeassess.ai.dto.AiDocumentExamGenerateResponse.builder()
                        .title("Xử lý mảng số nguyên: Tính tổng các số chẵn trong mảng")
                        .problemStatement("Cho một mảng gồm N số nguyên A[0], A[1], ..., A[N-1]. Hãy viết chương trình bằng ngôn ngữ Java để tính và in ra tổng của tất cả các phần tử có giá trị chẵn trong mảng. Nếu mảng không chứa bất kỳ số chẵn nào hoặc rỗng, hãy in ra 0.")
                        .inputSpecification("Dòng đầu tiên chứa số nguyên dương N (1 <= N <= 10^5) biểu thị số lượng phần tử của mảng.\nDòng thứ hai chứa N số nguyên A[i] (-10^9 <= A[i] <= 10^9), các số cách nhau bởi một khoảng trắng.")
                        .outputSpecification("In ra một số nguyên duy nhất là tổng các số chẵn trong mảng. Lưu ý: Tổng các phần tử có thể vượt quá giới hạn kiểu int 32-bit, do đó cần sử dụng kiểu dữ liệu 64-bit (long trong Java) để tích lũy biến tổng.")
                        .constraints("Thời gian chạy tối đa: 1000ms. Bộ nhớ tối đa: 256MB. 1 <= N <= 10^5; -10^9 <= A[i] <= 10^9.")
                        .sampleInput("5\n1 2 3 4 5")
                        .sampleOutput("6")
                        .programmingLanguage("Java")
                        .difficulty("EASY")
                        .sourceDocumentName("Tai_lieu_test_AI_tu_dong_sinh_de_lap_trinh.docx")
                        .documentTopicSummary("Cấu trúc mảng một chiều, duyệt phần tử và tính tổng các số chẵn trong Java")
                        .learningObjectives(java.util.List.of(
                                "Khai báo và duyệt mảng số nguyên bằng vòng lặp for/for-each trong Java",
                                "Sử dụng toán tử chia lấy dư (%) để nhận diện và lọc chính xác các phần tử số chẵn",
                                "Xử lý kiểu dữ liệu long cho biến tổng tránh lỗi tràn số nguyên (integer overflow)"
                        ))
                        .sourceReferences(java.util.List.of(
                                "Tài liệu học tập: Khởi tạo mảng int[] a = new int[n]",
                                "Yêu cầu bài toán: Duyệt mảng nếu a[i] % 2 == 0 thì cộng dồn vào tổng"
                        ))
                        .testCases(java.util.List.of(
                                com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                        .name("Test cơ bản - Mảng hỗn hợp chẵn lẻ")
                                        .description("Kiểm tra mảng gồm cả số chẵn và lẻ (1, 2, 3, 4, 5) -> Tổng = 6")
                                        .inputData("5\n1 2 3 4 5")
                                        .expectedOutput("6")
                                        .points(java.math.BigDecimal.valueOf(4.00))
                                        .isHidden(false)
                                        .build(),
                                com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                        .name("Test biên - Mảng chỉ toàn số lẻ")
                                        .description("Không có phần tử chẵn nào, kết quả mong đợi là 0")
                                        .inputData("4\n1 3 5 7")
                                        .expectedOutput("0")
                                        .points(java.math.BigDecimal.valueOf(3.00))
                                        .isHidden(false)
                                        .build(),
                                com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                        .name("Test nâng cao - Số âm và số lớn tránh tràn số")
                                        .description("Kiểm tra số âm chẵn (-2, -6) và số lớn 10^9 với kiểu long")
                                        .inputData("5\n-2 4 -6 1000000000 1000000000")
                                        .expectedOutput("1999999996")
                                        .points(java.math.BigDecimal.valueOf(3.00))
                                        .isHidden(true)
                                        .build()
                        ))
                        .rubricCriteria(java.util.List.of(
                                com.codeassess.ai.dto.AiRubricItemDto.builder()
                                        .name("Tính đúng đắn thuật toán & Duyệt mảng số chẵn")
                                        .description("Vòng lặp duyệt mảng chính xác và áp dụng điều kiện a[i] % 2 == 0 để tính tổng")
                                        .maxScore(java.math.BigDecimal.valueOf(5.00))
                                        .build(),
                                com.codeassess.ai.dto.AiRubricItemDto.builder()
                                        .name("Xử lý kiểu dữ liệu long & Trường hợp biên")
                                        .description("Khai báo biến tổng kiểu long chống tràn số; xử lý đúng mảng toàn lẻ hoặc số âm")
                                        .maxScore(java.math.BigDecimal.valueOf(3.00))
                                        .build(),
                                com.codeassess.ai.dto.AiRubricItemDto.builder()
                                        .name("Phong cách lập trình & Tối ưu thời gian O(N)")
                                        .description("Thuật toán duyệt O(N), mã nguồn sạch, cấu trúc rõ ràng và chuẩn quy ước Java")
                                        .maxScore(java.math.BigDecimal.valueOf(2.00))
                                        .build()
                        ))
                        .totalRubricScore(java.math.BigDecimal.valueOf(10.00))
                        .isSimulation(true)
                        .build();
                return (T) response;
            } else if (isStringTopic) {
                com.codeassess.ai.dto.AiDocumentExamGenerateResponse response = com.codeassess.ai.dto.AiDocumentExamGenerateResponse.builder()
                        .title("Xử lý chuỗi ký tự: Kiểm tra chuỗi đối xứng (Palindrome)")
                        .problemStatement("Cho một chuỗi ký tự S. Hãy viết chương trình bằng Java kiểm tra xem chuỗi S có phải là chuỗi đối xứng (Palindrome) hay không (không phân biệt chữ hoa, chữ thường). In ra 'YES' nếu đối xứng, ngược lại in ra 'NO'.")
                        .inputSpecification("Một dòng duy nhất chứa chuỗi ký tự S (1 <= length(S) <= 10^5).")
                        .outputSpecification("In ra 'YES' nếu chuỗi S là đối xứng, in ra 'NO' nếu không phải.")
                        .constraints("Thời gian chạy tối đa: 1000ms. Bộ nhớ tối đa: 256MB.")
                        .sampleInput("madam")
                        .sampleOutput("YES")
                        .programmingLanguage("Java")
                        .difficulty("EASY")
                        .sourceDocumentName("Tai_lieu_chuoi_ky_tu.docx")
                        .documentTopicSummary("Kỹ thuật hai con trỏ kiểm tra chuỗi đối xứng trong Java")
                        .learningObjectives(java.util.List.of(
                                "Thao tác với chuỗi ký tự trong Java qua String và charAt()",
                                "Ứng dụng kỹ thuật hai con trỏ so sánh từ hai đầu chuỗi",
                                "Tối ưu bộ nhớ O(1) và thời gian O(N)"
                        ))
                        .sourceReferences(java.util.List.of(
                                "Tài liệu học tập: Phương thức String.charAt(int index)",
                                "Định nghĩa: Chuỗi Palindrome đối xứng qua trục giữa"
                        ))
                        .testCases(java.util.List.of(
                                com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                        .name("Test cơ bản - Chuỗi đối xứng hợp lệ")
                                        .description("Kiểm tra chuỗi 'madam' đối xứng")
                                        .inputData("madam")
                                        .expectedOutput("YES")
                                        .points(java.math.BigDecimal.valueOf(5.00))
                                        .isHidden(false)
                                        .build(),
                                com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                        .name("Test chuỗi không đối xứng")
                                        .description("Kiểm tra chuỗi 'hello'")
                                        .inputData("hello")
                                        .expectedOutput("NO")
                                        .points(java.math.BigDecimal.valueOf(5.00))
                                        .isHidden(false)
                                        .build()
                        ))
                        .rubricCriteria(java.util.List.of(
                                com.codeassess.ai.dto.AiRubricItemDto.builder()
                                        .name("Tính đúng đắn thuật toán Palindrome")
                                        .description("So sánh chính xác các ký tự đối xứng từ hai đầu chuỗi")
                                        .maxScore(java.math.BigDecimal.valueOf(6.00))
                                        .build(),
                                com.codeassess.ai.dto.AiRubricItemDto.builder()
                                        .name("Tối ưu độ phức tạp & Clean Code")
                                        .description("Thời gian O(N), bộ nhớ O(1), mã nguồn sạch và dễ bảo trì")
                                        .maxScore(java.math.BigDecimal.valueOf(4.00))
                                        .build()
                        ))
                        .totalRubricScore(java.math.BigDecimal.valueOf(10.00))
                        .isSimulation(true)
                        .build();
                return (T) response;
            } else {
                com.codeassess.ai.dto.AiDocumentExamGenerateResponse response = com.codeassess.ai.dto.AiDocumentExamGenerateResponse.builder()
                        .title("Tìm đường đi ngắn nhất trên đồ thị (BFS / Shortest Path)")
                        .problemStatement("Cho đồ thị vô hướng gồm N đỉnh và M cạnh. Hãy tìm khoảng cách ngắn nhất từ đỉnh nguồn S đến tất cả các đỉnh còn lại sử dụng thuật toán Breadth-First Search (BFS). Nếu không có đường đi đến đỉnh nào, in ra -1 cho đỉnh đó.")
                        .inputSpecification("Dòng đầu tiên chứa 3 số nguyên N, M, S (1 <= N <= 10^4, 0 <= M <= 10^5, 1 <= S <= N).\nM dòng tiếp theo, mỗi dòng chứa 2 số nguyên u, v mô tả một cạnh vô hướng giữa u và v.")
                        .outputSpecification("In ra N số nguyên trên một dòng cách nhau bởi khoảng trắng, trong đó số thứ i là khoảng cách ngắn nhất từ S đến đỉnh i. Khoảng cách từ S đến chính nó là 0.")
                        .constraints("Thời gian chạy tối đa: 1000ms. Bộ nhớ tối đa: 256MB. Đồ thị không chứa đa cạnh hoặc khuyên.")
                        .sampleInput("4 3 1\n1 2\n2 3\n3 4")
                        .sampleOutput("0 1 2 3")
                        .programmingLanguage("Java")
                        .difficulty("MEDIUM")
                        .sourceDocumentName("Tài liệu bài giảng trích xuất")
                        .documentTopicSummary("Đồ thị, hàng đợi Queue và thuật toán tìm kiếm theo chiều rộng BFS")
                        .learningObjectives(java.util.List.of(
                                "Vận dụng cấu trúc dữ liệu Hàng đợi (Queue) để triển khai BFS",
                                "Tổ chức danh sách kề (Adjacency List) tối ưu bộ nhớ",
                                "Xử lý đỉnh không liên thông với giá trị mặc định -1"
                        ))
                        .sourceReferences(java.util.List.of(
                                "Phần III: Thuật toán duyệt đồ thị BFS trên danh sách kề",
                                "Công thức khoảng cách dist[v] = dist[u] + 1"
                        ))
                        .testCases(java.util.List.of(
                                com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                        .name("Test cơ bản - Đồ thị đường thẳng")
                                        .description("Kiểm tra đồ thị liên thông đơn giản")
                                        .inputData("4 3 1\n1 2\n2 3\n3 4")
                                        .expectedOutput("0 1 2 3")
                                        .points(java.math.BigDecimal.valueOf(4.00))
                                        .isHidden(false)
                                        .build(),
                                com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                        .name("Test đỉnh cô lập không liên thông")
                                        .description("Đỉnh không có đường đi từ đỉnh nguồn S")
                                        .inputData("4 1 1\n1 2")
                                        .expectedOutput("0 1 -1 -1")
                                        .points(java.math.BigDecimal.valueOf(3.00))
                                        .isHidden(false)
                                        .build(),
                                com.codeassess.ai.dto.AiTestCaseDraftDto.builder()
                                        .name("Test đồ thị có chu trình")
                                        .description("Kiểm tra cờ visited tránh lặp chu trình")
                                        .inputData("5 5 1\n1 2\n2 3\n3 1\n3 4\n4 5")
                                        .expectedOutput("0 1 1 2 3")
                                        .points(java.math.BigDecimal.valueOf(3.00))
                                        .isHidden(true)
                                        .build()
                        ))
                        .rubricCriteria(java.util.List.of(
                                com.codeassess.ai.dto.AiRubricItemDto.builder()
                                        .name("Tính đúng đắn thuật toán & Vượt qua Test Cases")
                                        .description("Đúng kết quả trên đồ thị liên thông, đồ thị rời rạc và chu trình")
                                        .maxScore(java.math.BigDecimal.valueOf(6.00))
                                        .build(),
                                com.codeassess.ai.dto.AiRubricItemDto.builder()
                                        .name("Tối ưu độ phức tạp O(V + E) & Clean Code")
                                        .description("Sử dụng Queue chuẩn, cấu trúc danh sách kề rõ ràng, biến đặt tên đúng quy ước")
                                        .maxScore(java.math.BigDecimal.valueOf(4.00))
                                        .build()
                        ))
                        .totalRubricScore(java.math.BigDecimal.valueOf(10.00))
                        .isSimulation(true)
                        .build();
                return (T) response;
            }
        }

        throw new UnsupportedOperationException("StubAiClient does not support structured output for: " + targetClass.getName());
    }
}
