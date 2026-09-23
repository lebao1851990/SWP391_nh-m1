const http = require('http');
const mysql = require('mysql2/promise');
const crypto = require('crypto');

function uuidv4() { return crypto.randomUUID(); }

async function testEndpoint(method, path, body = null, sessionCookie = null) {
    return new Promise((resolve, reject) => {
        const headers = { 'Content-Type': 'application/json' };
        if (sessionCookie) headers['Cookie'] = sessionCookie;

        const options = { hostname: 'localhost', port: 8080, path: path, method: method, headers };
        const req = http.request(options, (res) => {
            let data = ''; res.on('data', (c) => data += c); res.on('end', () => resolve({ status: res.statusCode, data }));
        });
        req.on('error', (e) => reject(e));
        if (body) req.write(JSON.stringify(body));
        req.end();
    });
}

async function login(email, password) {
    return new Promise((resolve, reject) => {
        const body = JSON.stringify({ email, password });
        const options = { hostname: 'localhost', port: 8080, path: '/api/auth/login', method: 'POST', headers: { 'Content-Type': 'application/json' } };
        const req = http.request(options, (res) => {
            let data = ''; res.on('data', (c) => data += c); res.on('end', () => {
                const cookieHeader = res.headers['set-cookie'];
                resolve({ status: res.statusCode, sessionCookie: cookieHeader ? cookieHeader[0] : null });
            });
        });
        req.on('error', (e) => reject(e));
        req.write(body); req.end();
    });
}

async function register(fullName, email, role, password = "Password123!") {
    await testEndpoint('POST', '/api/auth/register', {
        fullName, email, password, confirmPassword: password, accountRole: role, terms: true
    });
}

async function run() {
    console.log("Setting up DB...");
    const conn = await mysql.createConnection({ host: 'localhost', user: 'root', password: '123456', database: 'plagiarism_system' });

    // Clean up
    await conn.execute('DELETE FROM plagiarism_evidence');
    await conn.execute('DELETE FROM similarity_matrix');
    await conn.execute('DELETE FROM plagiarism_appeals');
    await conn.execute('DELETE FROM submissions');
    await conn.execute('DELETE FROM exams');
    await conn.execute('DELETE FROM classes');
    await conn.execute('DELETE FROM users WHERE email LIKE "%qa_idor%"');

    // Users
    await register("Student A", "studentA_qa_idor@uni.edu", "student");
    await register("Student B", "studentB_qa_idor@uni.edu", "student");
    await register("Teacher", "teacher_qa_idor@uni.edu", "teacher");
    
    // Get IDs
    const [rows] = await conn.execute('SELECT id, email FROM users WHERE email LIKE "%qa_idor%"');
    const uMap = {}; rows.forEach(r => uMap[r.email] = r.id);
    const stuA_id = uMap['studentA_qa_idor@uni.edu'];
    const stuB_id = uMap['studentB_qa_idor@uni.edu'];
    const tchr_id = uMap['teacher_qa_idor@uni.edu'];

    // Create Class & Exam
    const classId = uuidv4();
    await conn.execute(`INSERT INTO classes (id, name, code, semester, teacher_id) VALUES ('${classId}', 'Class', 'C', 'Sem', '${tchr_id}')`);
    const examId = uuidv4();
    await conn.execute(`INSERT INTO exams (id, class_id, title, status) VALUES ('${examId}', '${classId}', 'Exam', 'active')`);

    // Submissions
    const subA_id = uuidv4();
    const subB_id = uuidv4();
    await conn.execute(`INSERT INTO submissions (id, exam_id, student_id, source_code, status) VALUES ('${subA_id}', '${examId}', '${stuA_id}', 'Code A', 'GRADED')`);
    await conn.execute(`INSERT INTO submissions (id, exam_id, student_id, source_code, status) VALUES ('${subB_id}', '${examId}', '${stuB_id}', 'Code B', 'GRADED')`);

    // Similarity Pair
    const pairId = 9991;
    await conn.execute(`INSERT INTO similarity_matrix (id, exam_id, submission_a_id, submission_b_id, similarity_rate, is_flagged) VALUES (${pairId}, '${examId}', '${subA_id}', '${subB_id}', 85.5, 1)`);
    await conn.execute(`INSERT INTO plagiarism_evidence (id, similarity_matrix_id, evidence_type, description, submission_a_start_line, submission_a_end_line, submission_b_start_line, submission_b_end_line) VALUES (uuid(), ${pairId}, 'AST_MATCH', 'Evidence desc', 1, 10, 1, 10)`);

    // Appeals
    const resAppealA = await testEndpoint('POST', '/api/student/appeals', { submissionId: subA_id, basis: "FALSE_POSITIVE", explanation: "App A" }, (await login("studentA_qa_idor@uni.edu", "Password123!")).sessionCookie);
    const appealA_id = JSON.parse(resAppealA.data).id;
    
    const resAppealB = await testEndpoint('POST', '/api/student/appeals', { submissionId: subB_id, basis: "FALSE_POSITIVE", explanation: "App B" }, (await login("studentB_qa_idor@uni.edu", "Password123!")).sessionCookie);
    const appealB_id = JSON.parse(resAppealB.data).id;

    console.log("Setup complete. Running tests...\\n");
    
    const cookieA = (await login("studentA_qa_idor@uni.edu", "Password123!")).sessionCookie;
    const cookieB = (await login("studentB_qa_idor@uni.edu", "Password123!")).sessionCookie;
    const cookieT = (await login("teacher_qa_idor@uni.edu", "Password123!")).sessionCookie;

    function report(name, expected, res) {
        console.log(`[${name}] => ${res.status} (Expected: ${expected})`);
        console.log(`Data: ${res.data.substring(0, 100)}...`);
    }

    console.log("=== GROUP A: SUBMISSION OWNERSHIP ===");
    report("A1: Stu A GET own submission", 200, await testEndpoint('GET', `/api/student/submissions/${subA_id}/result`, null, cookieA));
    report("A2: Stu A GET Stu B submission", 404, await testEndpoint('GET', `/api/student/submissions/${subB_id}/result`, null, cookieA));
    report("A3: Stu B GET Stu A submission", 404, await testEndpoint('GET', `/api/student/submissions/${subA_id}/result`, null, cookieB));

    console.log("\\n=== GROUP B: PLAGIARISM OWNERSHIP & LEAKAGE ===");
    const plA = await testEndpoint('GET', `/api/student/plagiarism/${pairId}`, null, cookieA);
    report("Stu A GET plagiarism", 200, plA);
    if(plA.status === 200) {
        const data = JSON.parse(plA.data);
        console.log("Contains otherStudentId?", data.hasOwnProperty('otherStudentId') || JSON.stringify(data).includes(stuB_id) ? "YES (FAIL)" : "NO (PASS)");
        console.log("Contains otherStudentName?", data.hasOwnProperty('otherStudentName') || JSON.stringify(data).includes("Student B") ? "YES (FAIL)" : "NO (PASS)");
        console.log("Contains hash_value?", JSON.stringify(data).includes("secret_hash_xyz") ? "YES (FAIL)" : "NO (PASS)");
        console.log("Contains other's code?", JSON.stringify(data).includes("Code B") ? "YES (FAIL)" : "NO (PASS)");
    }
    // Test a fake pair where neither is student A
    await conn.execute(`INSERT INTO similarity_matrix (id, exam_id, submission_a_id, submission_b_id, similarity_rate, is_flagged) VALUES (9992, '${examId}', '${subB_id}', '${subB_id}', 99.0, 1)`);
    report("Stu A GET unrelated plagiarism", 404, await testEndpoint('GET', `/api/student/plagiarism/9992`, null, cookieA));

    console.log("\\n=== GROUP C: APPEAL OWNERSHIP ===");
    report("C1: Stu A GET own appeal", 200, await testEndpoint('GET', `/api/student/appeals/${appealA_id}`, null, cookieA));
    report("C2: Stu A GET Stu B appeal", 404, await testEndpoint('GET', `/api/student/appeals/${appealB_id}`, null, cookieA));
    report("C3: Stu B GET Stu A appeal", 404, await testEndpoint('GET', `/api/student/appeals/${appealA_id}`, null, cookieB));

    console.log("\\n=== GROUP D: ROLE BOUNDARY ===");
    report("Unauth GET submission", 401, await testEndpoint('GET', `/api/student/submissions/${subA_id}/result`, null, null));
    report("Teacher GET student submission endpoint", 403, await testEndpoint('GET', `/api/student/submissions/${subA_id}/result`, null, cookieT));
    // Admin GET is not possible easily unless we upgrade a user to admin

    console.log("\\n=== GROUP E: INVALID/NONEXISTENT IDs ===");
    report("Invalid UUID submission", 404, await testEndpoint('GET', `/api/student/submissions/fake-id/result`, null, cookieA));
    report("Nonexistent ID plagiarism", 404, await testEndpoint('GET', `/api/student/plagiarism/9999`, null, cookieA));
    report("Nonexistent ID appeal", 404, await testEndpoint('GET', `/api/student/appeals/opaque_xyz`, null, cookieA));

    await conn.end();
}

run();
