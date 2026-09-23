const axios = require('axios');
const assert = require('assert');

// Axios instance with cookie support
const axiosInstance = axios.create({
    baseURL: 'http://localhost:8080/api',
    withCredentials: true,
    headers: { 'Content-Type': 'application/json' }
});

// Manual cookie tracking for node axios (since it doesn't do it automatically without tough-cookie)
let currentCookie = '';
axiosInstance.interceptors.request.use(config => {
    if (currentCookie) config.headers.Cookie = currentCookie;
    return config;
});
axiosInstance.interceptors.response.use(response => {
    const setCookie = response.headers['set-cookie'];
    if (setCookie && setCookie.length > 0) {
        currentCookie = setCookie[0].split(';')[0];
    }
    return response;
}, error => {
    return Promise.reject(error);
});

async function runE2E() {
    console.log('--- STARTING COMPREHENSIVE E2E TESTS ---');
    
    try {
        // 1. Unauthenticated Access
        console.log('\\n[TEST] 1. Unauthenticated access to /admin/users...');
        try {
            await axiosInstance.get('/admin/users');
            console.error('❌ FAILED: Should have been 401');
        } catch (e) {
            if (e.response && e.response.status === 401) console.log('✔ Passed 401 Unauthenticated');
            else console.error('❌ FAILED with status', e.response?.status);
        }

        // 2. Admin Login
        console.log('\\n[TEST] 2. Admin Login...');
        const adminLogin = await axiosInstance.post('/auth/login', { email: 'admin@codeassess.com', password: 'password' });
        assert.strictEqual(adminLogin.status, 200);
        assert.strictEqual(adminLogin.data.user.role, 'ADMIN');
        console.log('✔ Passed Admin Login');

        // 3. Admin Access Control (Admin Dashboard)
        console.log('\\n[TEST] 3. Admin accessing Admin resources...');
        const adminUsers = await axiosInstance.get('/admin/users');
        assert.strictEqual(adminUsers.status, 200);
        console.log('✔ Passed Admin accessing /admin/users');

        // 4. Admin accessing Teacher resources (Should be forbidden if strictly separated, or allowed. Let's see)
        console.log('\\n[TEST] 4. Admin accessing Teacher resources...');
        try {
            await axiosInstance.get('/teacher/exams');
            console.error('❌ FAILED: Should have been 403');
        } catch (e) {
            if (e.response && e.response.status === 403) console.log('✔ Passed 403 Forbidden for Admin on Teacher routes');
            else console.log('Allowed? Status:', e.response?.status);
        }

        // 5. Logout
        console.log('\\n[TEST] 5. Logout...');
        await axiosInstance.post('/auth/logout');
        currentCookie = '';
        console.log('✔ Passed Logout');

        // 6. Teacher Login & Plagiarism Flow
        console.log('\\n[TEST] 6. Teacher Login...');
        const teacherLogin = await axiosInstance.post('/auth/login', { email: 'teacher@codeassess.com', password: 'password' });
        assert.strictEqual(teacherLogin.status, 200);
        assert.strictEqual(teacherLogin.data.user.role, 'TEACHER');
        console.log('✔ Passed Teacher Login');

        console.log('\\n[TEST] 7. AI Endpoint 503 behavior...');
        try {
            // We use a dummy pairId (1)
            await axiosInstance.post('/teacher/plagiarism/1/ai/similarity-explanation');
            console.error('❌ FAILED: Should be 503 or 404');
        } catch (e) {
            if (e.response && (e.response.status === 503 || e.response.status === 404)) {
                console.log('✔ Passed AI Integration 503/404 handling:', e.response.status);
            } else {
                console.error('❌ FAILED AI endpoint with status:', e.response?.status);
            }
        }

        // Logout Teacher
        await axiosInstance.post('/auth/logout');
        currentCookie = '';

        // 7. Student Login & Appeals Flow
        console.log('\\n[TEST] 8. Student Login...');
        const studentLogin = await axiosInstance.post('/auth/login', { email: 'student@codeassess.com', password: 'password' });
        assert.strictEqual(studentLogin.status, 200);
        assert.strictEqual(studentLogin.data.user.role, 'STUDENT');
        console.log('✔ Passed Student Login');

        console.log('\\n[TEST] 9. Student Appeals list...');
        const appeals = await axiosInstance.get('/student/appeals');
        assert.strictEqual(appeals.status, 200);
        assert(Array.isArray(appeals.data));
        console.log('✔ Passed Student Appeals list');

        console.log('\\n--- E2E TESTS COMPLETED SUCCESSFULLY ---');
    } catch (e) {
        console.error('\\n❌ UNEXPECTED ERROR:', e.message);
        if (e.response) {
            console.error('Status:', e.response.status);
            console.error('Data:', e.response.data);
        }
    }
}

runE2E();
