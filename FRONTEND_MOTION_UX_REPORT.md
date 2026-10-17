# CODEASSESS FRONTEND — BÁO CÁO HỆ THỐNG CHUYỂN ĐỘNG VÀ TRẢI NGHIỆM NGƯỜI DÙNG (FRONTEND MOTION & UX REPORT)

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Phiên bản:** Release Milestone 2 (Motion & UX Hardening)  
**Ngày phát hành:** 15/10/2026  
**Đơn vị thực hiện:** Senior Frontend & UX Architecture Team  

---

## 1. Hệ thống Chuyển trang (Page Transition System)

### 1.1. Kiến trúc Chuyển trang Tối giản & Hiệu năng cao
Hệ thống chuyển trang được xây dựng trực tiếp trên cơ chế gốc của **Next.js App Router**, kết hợp giữa:
1. **Persistent Shell Layout:** Cả [`TeacherLayout`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/teacher/layout.tsx) và [`StudentLayout`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/student/layout.tsx) giữ nguyên Sidebar và Header cố định trên DOM, không bị re-mount khi chuyển đổi giữa các trang con.
2. **Next.js Template (`template.tsx`):** Sử dụng [`TeacherTemplate`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/teacher/template.tsx) và [`StudentTemplate`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/student/template.tsx) bao bọc nội dung động trong class `.page-transition`. Mỗi lần route thay đổi, Next.js tự động tạo một instance template mới với hiệu ứng mượt mà.
3. **Instant Loading Skeletons (`loading.tsx`):** Bổ sung [`TeacherLoading`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/teacher/loading.tsx) và [`StudentLoading`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/student/loading.tsx) mô phỏng cấu trúc lưới banner, cards và table của trang, loại bỏ hoàn toàn hiện tượng nháy trắng (white flash) hoặc giật cục layout (layout shift) khi tải dữ liệu từ máy chủ.

### 1.2. Tham số Kỹ thuật Chuyển động
- **Thời lượng:** `180ms` (Nằm trong dải chuẩn mực `150–250ms`).
- **Timing Function:** `cubic-bezier(0.16, 1, 0.3, 1)` (Easing hiện đại, bắt đầu nhanh và giảm tốc mềm mại).
- **Thuộc tính biến đổi:** `opacity: 0.92 → 1.0` kết hợp `transform: translateY(4px) → translateY(0)`.
- **Tối ưu hóa GPU:** Sử dụng `will-change: opacity, transform` để trình duyệt ưu tiên phân bổ tài nguyên render phần cứng.

### 1.3. Hỗ trợ Giảm chuyển động (`prefers-reduced-motion`)
Nhằm tuân thủ chuẩn tiếp cận người dùng khuyết tật (WCAG 2.1 Level AAA), hệ thống bổ sung media query trong `globals.css`:
```css
@media (prefers-reduced-motion: reduce) {
  .page-transition {
    animation: none !important;
    will-change: auto;
  }
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```
Khi người dùng kích hoạt cài đặt giảm chuyển động trên hệ điều hành (Windows Settings > Accessibility > Visual Effects > Animation Effects = Off), toàn bộ hiệu ứng chuyển trang và phóng to/thu nhỏ sẽ tức thì được vô hiệu hóa mà không làm ảnh hưởng đến chức năng ứng dụng.

---

## 2. Tiêu chuẩn Vi tương tác (Micro-Interactions Standards)

### 2.1. Nút bấm (Button States & Double-click Prevention)
- **Hover & Active:** Hiệu ứng làm sáng hoặc tối nhẹ dải màu nền (`hover:bg-primary/90`, `active:scale-[0.98]`), tạo cảm giác bấm vật lý chân thực.
- **Loading State:** Khi một mutation bất đồng bộ đang chạy (`createExam.isPending`, `submitExam.isPending`, `saveScoring.isPending`), nút tự động kích hoạt spinner `Loader2 animate-spin`, giữ nguyên nhãn hành động dễ hiểu (ví dụ: *"Submitting Solution..."* hoặc *"Saving Scoring..."*).
- **Phòng ngừa Thao tác Kép (Defensive UX):** Nút tự động chuyển trạng thái `disabled:opacity-50 pointer-events-none`, ngăn chặn người dùng vô tình bấm đúp nhiều lần gây lỗi gửi đơn hoặc nộp bài trùng lặp.

### 2.2. Biểu mẫu & Nhập liệu (Form & Input Interactions)
- **Focus Rings:** Toàn bộ ô input, textarea, select đều có viền `focus:ring-1 focus:ring-primary focus:border-primary focus:outline-none`, màu xanh tương phản cao giúp định vị tiêu điểm bàn phím.
- **Bảo lưu Dữ liệu khi Gặp lỗi:** Khi API trả về lỗi 400 hoặc 500, dữ liệu sinh viên đã gõ (code giải bài, lý do khiếu nại, rubric score) được giữ nguyên vẹn trên state, không bị reset trắng form gây ức chế cho người dùng.
- **Bộ đếm Ký tự Trực tiếp:** Áp dụng trên ô nhập giải trình khiếu nại (20 đến 2000 ký tự) và khung soạn thảo code (Lines & Characters), cập nhật đồng bộ theo từng phím gõ.

### 2.3. Hộp thoại & Menu (Dialogs, Dropdowns & Drawers)
- **Enter/Exit Transition:** Sử dụng Tailwind Animate (`animate-in fade-in zoom-in-95 duration-150`), tạo cảm giác xuất hiện thanh thoát từ trung tâm màn hình.
- **Đóng An toàn:** Hỗ trợ đầy đủ phím `Escape` và sự kiện click ra ngoài vùng backdrop (`mousedown outside`), tự động giải phóng scrim và con trỏ chuột, không để lại lớp phủ tàng hình chặn màn hình.
- **Hộp thoại Xác nhận Trước Hành động Quan trọng:**
  - Xác nhận nộp bài thi chính thức (ngăn nộp nhầm khi chưa code xong).
  - Xác nhận ghi đè điểm số của Giảng viên (yêu cầu điền lý do giải trình).

### 2.4. Bảng Dữ liệu (Data Tables)
- **Row Hover:** Tinh tế và trang nhã (`hover:bg-surface-container-low/60 transition-colors`).
- **Thanh lọc & Tìm kiếm:** Tích hợp bộ lọc đa tiêu chí, đếm số lượng bản ghi trên từng tab (Pending, Approved, Rejected) và nút xóa nhanh từ khóa tìm kiếm (`X`).
- **Trạng thái Dữ liệu Rỗng (Empty States):** Có minh họa vector Lucide, giải thích cụ thể lý do không có dữ liệu và cung cấp nút hành động tiếp theo rõ ràng (ví dụ: *"No exams found. Click '+ Create Exam' to schedule one"*).

### 2.5. Thông báo Toasts
- Sử dụng thư viện **Sonner** đồng bộ góc trên/dưới phải:
  - **Success:** Xanh lá (`toast.success`), chỉ hiển thị sau khi máy chủ đã xác nhận lưu thành công.
  - **Error:** Đỏ hồng (`toast.error`), hiển thị chính xác thông điệp lỗi từ backend DTO.
  - **Warning/Info:** Vàng/Xanh dương (`toast.info`).
  - **Chống Spam Toast:** Mỗi thao tác đơn lẻ chỉ kích hoạt 1 thông báo tương ứng.

---

## 3. Khả năng Tiếp cận (Accessibility — a11y)

1. **Điều hướng bằng Bàn phím (Keyboard Navigation):**
   - Hỗ trợ phím tắt `Ctrl + K` / `⌘K` để focus nhanh vào thanh tìm kiếm toàn cục từ bất kỳ vị trí nào trên ứng dụng.
   - Các nút bấm, link và menu item đều có thứ tự tab (`tabindex`) tự nhiên, hiển thị outline tiêu điểm rõ ràng.
2. **Độ tương phản Màu sắc (Color Contrast):**
   - Nền sáng `bg-surface (#f8fafc)` đi kèm chữ tối `text-on-surface (#0f172a)` và `text-on-surface-variant (#475569)`, đạt tỷ lệ tương phản vượt mức 7:1 (chuẩn WCAG AAA cho văn bản thông thường).
3. **Môi trường Lập trình & So sánh Code:**
   - Khung Code Editor và 2 cột Plagiarism Diff sử dụng phông chữ lập trình chuẩn monospaced (`JetBrains Mono / Fira Code`), nền tối sâu `bg-slate-950` chống mỏi mắt cho Giảng viên khi phải thẩm định mã nguồn trong thời gian dài.
   - Các dòng vi phạm AST được tô viền màu đỏ nổi bật (`border-l-2 border-rose-500 bg-rose-950/60`), có số dòng đối ứng chuẩn xác giữa hai tác giả.

---

## 4. Tính Đáp ứng Thiết bị (Responsive Design)

| Phân giải Thiết bị | Bố cục Layout Đã Kiểm tra | Trạng thái Hoạt động |
|---|---|:---:|
| **Desktop (>= 1280px)** | Sidebar cố định 260px, Header trải rộng, Bảng dữ liệu 4-5 cột, 4 thẻ thống kê ngang. | **PASS** (Tối ưu hoàn hảo) |
| **Tablet (768px – 1024px)** | Grid 2 cột tự co giãn, bảng hỗ trợ cuộn ngang mượt mà (`overflow-x-auto`), Code viewer duy trì thanh cuộn nội tại. | **PASS** (Không tràn layout) |
| **Mobile (< 768px)** | Form xếp chồng 1 cột, các nút hành động dàn phẳng dọc, bộ lọc chuyển dạng cuộn ngang linh hoạt. | **PASS** (Dễ thao tác chạm) |

---

## 5. Kết luận

Hệ thống chuyển động và trải nghiệm người dùng mới của CodeAssess đã đạt độ hoàn thiện cao, vận hành êm ái, đáp ứng tính nghiêm túc của một nền tảng học thuật mà không làm chậm tốc độ thao tác của người dùng.
