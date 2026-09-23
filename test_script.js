const http = require('http');

function request(method, path, data, headers = {}) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: 8081,
      path,
      method,
      headers: {
        'Content-Type': 'application/json',
        ...headers
      }
    };
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, headers: res.headers, data: body ? JSON.parse(body) : null });
        } catch(e) {
          resolve({ status: res.statusCode, headers: res.headers, data: body });
        }
      });
    });
    req.on('error', reject);
    if (data) {
      req.write(JSON.stringify(data));
    }
    req.end();
  });
}

async function run() {
  console.log("=== TESTING BACKEND APIs ===");
  
  // 1. Register Student
  console.log("Registering Student...");
  let res = await request('POST', '/api/auth/register', {
    email: "studentA@fpt.edu.vn",
    password: "password123",
    confirmPassword: "password123",
    fullName: "Student A",
    accountRole: "student",
    terms: true
  });
  console.log("Register Student:", res.status, res.data);

  // 2. Register Teacher
  console.log("Registering Teacher...");
  res = await request('POST', '/api/auth/register', {
    email: "teacherA@fpt.edu.vn",
    password: "password123",
    confirmPassword: "password123",
    fullName: "Teacher A",
    accountRole: "teacher",
    terms: true
  });
  console.log("Register Teacher:", res.status, res.data);

  // 3. Login Teacher
  console.log("Logging in Teacher...");
  res = await request('POST', '/api/auth/login', {
    email: "teacherA@fpt.edu.vn",
    password: "password123"
  });
  console.log("Login Teacher:", res.status, res.data);
  const teacherCookies = res.headers['set-cookie'];
  
  // 4. Login Student
  console.log("Logging in Student...");
  res = await request('POST', '/api/auth/login', {
    email: "studentA@fpt.edu.vn",
    password: "password123"
  });
  console.log("Login Student:", res.status, res.data);
  const studentCookies = res.headers['set-cookie'];

  // Get Student Exams
  console.log("Fetching Student Exams...");
  res = await request('GET', '/api/student/exams', null, { Cookie: studentCookies });
  console.log("Student Exams:", res.status, res.data);

  // Get Student Submissions
  console.log("Fetching Student Submissions...");
  res = await request('GET', '/api/student/submissions', null, { Cookie: studentCookies });
  console.log("Student Submissions:", res.status, res.data);

  // Teacher tests IDOR / missing exam
  console.log("Teacher accessing missing exam...");
  res = await request('GET', '/api/teacher/exams/non-existent-exam/submissions', null, { Cookie: teacherCookies });
  console.log("Teacher Exam Roster:", res.status, res.data);
  
  // Student trying to access teacher endpoint
  console.log("Student accessing teacher exam endpoint...");
  res = await request('GET', '/api/teacher/exams/non-existent-exam/submissions', null, { Cookie: studentCookies });
  console.log("Student accessing Teacher API:", res.status, res.data);
}
run();
