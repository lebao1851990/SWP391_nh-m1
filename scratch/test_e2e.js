// scratch/test_e2e.js
const fs = require('fs');
const path = require('path');

async function run() {
  const backendUrl = 'http://localhost:8080';
  const examId = '71463bd5-749e-46eb-9836-b99a3905a19b';

  console.log('--- 1. Login Student 1 ---');
  const loginRes = await fetch(`${backendUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'student.test@codeassess.edu', password: 'Password1234!' })
  });
  const loginData = await loginRes.json();
  console.log('Login Status:', loginRes.status, 'User:', loginData.user?.fullName);
  const token = loginData.token;

  console.log('\n--- 2. Upload student1_solution.zip ---');
  const zipPath = path.resolve(__dirname, 'e2e_workspaces/student1_solution.zip');
  const zipBuffer = fs.readFileSync(zipPath);
  console.log('ZIP Buffer size:', zipBuffer.length, 'bytes');

  const formData = new FormData();
  const blob = new Blob([zipBuffer], { type: 'application/zip' });
  formData.append('file', blob, 'student1_solution.zip');

  const uploadRes = await fetch(`${backendUrl}/api/student/exams/${examId}/submissions`, {
    method: 'POST',
    headers: {
      'X-Auth-Token': token
    },
    body: formData
  });

  const uploadData = await uploadRes.json();
  console.log('Upload Status:', uploadRes.status);
  console.log('Upload Response:', JSON.stringify(uploadData, null, 2));
}

run().catch(console.error);
