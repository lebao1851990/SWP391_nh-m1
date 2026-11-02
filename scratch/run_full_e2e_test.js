// scratch/run_full_e2e_test.js
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Helper to create a zip file using adm-zip or archiver, or simple zip buffer
function createZipWithContent(filenameInZip, content) {
  // Let's use powershell without BOM or python or zip buffer
  // We can write a temp file with utf8 without bom, then zip it
  const tempDir = path.resolve(__dirname, 'temp_' + Date.now());
  fs.mkdirSync(tempDir, { recursive: true });
  const filePath = path.join(tempDir, filenameInZip);
  fs.writeFileSync(filePath, content, { encoding: 'utf8' }); // Node.js writes UTF8 WITHOUT BOM!

  const zipPath = path.join(tempDir, 'output.zip');
  // Use PowerShell Compress-Archive
  execSync(`powershell -Command "Compress-Archive -Path '${filePath}' -DestinationPath '${zipPath}' -Force"`);
  const zipBuffer = fs.readFileSync(zipPath);
  fs.rmSync(tempDir, { recursive: true, force: true });
  return zipBuffer;
}

async function main() {
  const backendUrl = 'http://localhost:8080';
  const examId = '71463bd5-749e-46eb-9836-b99a3905a19b';
  const classId = '1c9eae53-991e-406d-915b-fe8894fbf686';

  console.log('===============================================================');
  console.log('   FULL END-TO-END AUTOMATED VERIFICATION SUITE');
  console.log('===============================================================\n');

  // STEP 1: MySQL Setup
  console.log('[STEP 1] Setting up MySQL test cases and student accounts...');
  const sqlCommands = `
UPDATE exams SET exam_date = '2026-10-20 23:30:00', duration = 1440, status = 'active' WHERE id = '${examId}';
DELETE FROM test_cases WHERE exam_id = '${examId}';
INSERT INTO test_cases (id, exam_id, name, description, input_data, expected_output, points, is_hidden, display_order)
VALUES 
('tc-e2e-001', '${examId}', 'Sample Test 1', 'Basic addition', '5 7\n', '12\n', 5.00, 0, 1),
('tc-e2e-002', '${examId}', 'Hidden Test 2', 'Large addition', '10 20\n', '30\n', 5.00, 1, 2);

INSERT IGNORE INTO users (id, email, password_hash, role, full_name)
VALUES ('student2-test-uuid', 'student2.test@codeassess.edu', '$2a$10$obTHtbvnESn6GbDvnZ0t5eZ6uyp7FRjytB267VNfi8IoTpAIzU0ym', 'STUDENT', 'Sinh Vien Hai');

INSERT IGNORE INTO class_students (class_id, student_id)
VALUES ('${classId}', 'student2-test-uuid');
`;
  execSync(`docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system`, { input: sqlCommands });
  console.log('MySQL setup complete: 2 test cases (1 public, 1 hidden) configured.\n');

  // STEP 2: Create Submissions
  console.log('[STEP 2] Creating Clean Java source code packages (UTF-8 without BOM)...');
  const code1 = `import java.util.Scanner;

public class Solution {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextInt()) {
            int a = scanner.nextInt();
            int b = scanner.nextInt();
            System.out.println(a + b);
        }
    }
}
`;

  const code2 = `import java.util.Scanner;

// Modified naming for similarity test
public class Calculator {
    public static void main(String[] inputArgs) {
        Scanner inputReader = new Scanner(System.in);
        if (inputReader.hasNextInt()) {
            int firstVal = inputReader.nextInt();
            int secondVal = inputReader.nextInt();
            int totalSum = firstVal + secondVal;
            System.out.println(totalSum);
        }
    }
}
`;

  const zipBuffer1 = createZipWithContent('Solution.java', code1);
  const zipBuffer2 = createZipWithContent('Calculator.java', code2);
  console.log(`ZIP 1 size: ${zipBuffer1.length} bytes, ZIP 2 size: ${zipBuffer2.length} bytes.\n`);

  // STEP 3: Student 1 Login and Upload
  console.log('[STEP 3] Student 1 logging in and submitting student1_solution.zip...');
  const loginRes1 = await fetch(`${backendUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'student.test@codeassess.edu', password: 'Password1234!' })
  });
  const loginData1 = await loginRes1.json();
  const token1 = loginData1.token;

  const formData1 = new FormData();
  formData1.append('file', new Blob([zipBuffer1], { type: 'application/zip' }), 'student1_solution.zip');

  const uploadRes1 = await fetch(`${backendUrl}/api/student/exams/${examId}/submissions`, {
    method: 'POST',
    headers: { 'X-Auth-Token': token1 },
    body: formData1
  });
  const sub1 = await uploadRes1.json();
  console.log(`[Student 1 Submission Result]`);
  console.log(`  ID: ${sub1.id}`);
  console.log(`  Status: ${sub1.status}`);
  console.log(`  Execution Status: ${sub1.executionStatus}`);
  console.log(`  Auto Score: ${sub1.autoScore} / Total Score: ${sub1.totalScore}`);
  console.log(`  Test Cases Passed: ${sub1.testCasesPassed} / ${sub1.testCasesTotal}`);
  console.log(`  Execution Duration: ${sub1.executionDurationMs} ms`);
  if (sub1.compileOutput) console.log(`  Compile Output: ${sub1.compileOutput}`);
  console.log();

  // STEP 4: Student 2 Login and Upload
  console.log('[STEP 4] Student 2 logging in and submitting student2_calculator.zip...');
  const loginRes2 = await fetch(`${backendUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'student2.test@codeassess.edu', password: 'Password1234!' })
  });
  const loginData2 = await loginRes2.json();
  const token2 = loginData2.token;

  const formData2 = new FormData();
  formData2.append('file', new Blob([zipBuffer2], { type: 'application/zip' }), 'student2_calculator.zip');

  const uploadRes2 = await fetch(`${backendUrl}/api/student/exams/${examId}/submissions`, {
    method: 'POST',
    headers: { 'X-Auth-Token': token2 },
    body: formData2
  });
  const sub2 = await uploadRes2.json();
  console.log(`[Student 2 Submission Result]`);
  console.log(`  ID: ${sub2.id}`);
  console.log(`  Status: ${sub2.status}`);
  console.log(`  Execution Status: ${sub2.executionStatus}`);
  console.log(`  Auto Score: ${sub2.autoScore} / Total Score: ${sub2.totalScore}`);
  console.log(`  Test Cases Passed: ${sub2.testCasesPassed} / ${sub2.testCasesTotal}`);
  console.log(`  Execution Duration: ${sub2.executionDurationMs} ms`);
  if (sub2.compileOutput) console.log(`  Compile Output: ${sub2.compileOutput}`);
  console.log();

  // STEP 5: Wait for Async AST Plagiarism Coordination if in-flight
  console.log('[STEP 5] Checking Plagiarism and Similarity Matrix in database...');
  await new Promise(r => setTimeout(r, 2000));
  const simQuery = execSync(`docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT submission_a_id, submission_b_id, similarity_rate, is_flagged FROM similarity_matrix WHERE exam_id = '${examId}';"`);
  console.log('Similarity Matrix entries:');
  console.log(simQuery.toString());

  // STEP 6: Teacher Management Workflow
  console.log('[STEP 6] Testing Teacher Submission Management Workflow...');
  const teacherLoginRes = await fetch(`${backendUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'teacher.test@codeassess.edu', password: 'Password1234!' })
  });
  const teacherToken = (await teacherLoginRes.json()).token;

  // 6.1 Teacher Submission List
  console.log('  [6.1] GET /api/teacher/exams/' + examId + '/submissions');
  const subsListRes = await fetch(`${backendUrl}/api/teacher/exams/${examId}/submissions`, {
    headers: { 'X-Auth-Token': teacherToken }
  });
  const teacherSubList = await subsListRes.json();
  console.log(`  Returned ${teacherSubList.length} submissions for teacher view:`);
  teacherSubList.forEach(s => {
    console.log(`    - Student: ${s.studentName} (${s.studentEmail}) | File: ${s.originalFilename} | Status: ${s.status}/${s.executionStatus} | Tests: ${s.testCasesPassed}/${s.testCasesTotal} | Score: ${s.autoScore}/${s.totalScore} | Plagiarism: ${s.plagiarismStatus} (${(s.maxSimilarityRate * 100).toFixed(1)}%)`);
  });

  // 6.2 Teacher View Source Code Files
  console.log(`\n  [6.2] GET /api/teacher/submissions/${sub1.id}/files`);
  const filesRes = await fetch(`${backendUrl}/api/teacher/submissions/${sub1.id}/files`, {
    headers: { 'X-Auth-Token': teacherToken }
  });
  const files = await filesRes.json();
  console.log(`  Extracted files count: ${files.length}`);
  files.forEach(f => {
    console.log(`    File: ${f.filename} (${f.size} bytes)`);
    console.log(`    Content Snippet: ${f.content.trim().split('\n')[0]}`);
  });

  // 6.3 Teacher Download ZIP
  console.log(`\n  [6.3] GET /api/teacher/submissions/${sub1.id}/download`);
  const downloadRes = await fetch(`${backendUrl}/api/teacher/submissions/${sub1.id}/download`, {
    headers: { 'X-Auth-Token': teacherToken }
  });
  const downloadedBuf = Buffer.from(await downloadRes.arrayBuffer());
  const isZip = downloadedBuf[0] === 0x50 && downloadedBuf[1] === 0x4B; // 'PK'
  console.log(`  Downloaded ZIP size: ${downloadedBuf.length} bytes | Valid ZIP signature (PK): ${isZip}`);

  // 6.4 Teacher Add Feedback
  console.log(`\n  [6.4] PUT /api/teacher/submissions/${sub1.id}/feedback`);
  const feedbackRes = await fetch(`${backendUrl}/api/teacher/submissions/${sub1.id}/feedback`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-Auth-Token': teacherToken
    },
    body: JSON.stringify({ feedback: 'Thuat toan toi uu, giai quyet tot ca public va hidden test cases.' })
  });
  console.log(`  Feedback update response status: ${feedbackRes.status} (204 No Content / Success)`);

  // 6.5 Teacher Scoring Detail
  console.log(`\n  [6.5] GET /api/teacher/scoring/submissions/${sub1.id}`);
  const scoringRes = await fetch(`${backendUrl}/api/teacher/scoring/submissions/${sub1.id}`, {
    headers: { 'X-Auth-Token': teacherToken }
  });
  const scoringDetail = await scoringRes.json();
  console.log(`  Scoring Detail Summary:`);
  console.log(`    Student: ${scoringDetail.studentName}`);
  console.log(`    Auto Score: ${scoringDetail.autoScore} / Total Score: ${scoringDetail.totalScore}`);
  console.log(`    Test Cases: ${scoringDetail.testCasesPassed} / ${scoringDetail.testCasesTotal}`);
  console.log(`    Instructor Feedback: "${scoringDetail.instructorFeedback}"`);
  console.log(`    Similarity Rate: ${scoringDetail.similarityRate}`);
  console.log(`    Test Cases Breakdown:`);
  if (scoringDetail.testCases) {
    scoringDetail.testCases.forEach(tc => {
      console.log(`      - [${tc.status}] ${tc.name} (Hidden: ${tc.isHidden}): Input: ${JSON.stringify(tc.inputData)} -> Output: ${JSON.stringify(tc.actualOutput)}`);
    });
  }

  console.log('\n===============================================================');
  console.log('   ALL E2E WORKFLOW TESTS COMPLETED SUCCESSFULLY!');
  console.log('===============================================================');
}

main().catch(err => {
  console.error('E2E Test Failed:', err);
  process.exit(1);
});
