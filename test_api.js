const axios = require('axios');

async function test() {
    try {
        console.log('Testing Login API...');
        const res = await axios.post('http://localhost:8080/api/auth/login', {
            email: 'admin@codeassess.com',
            password: 'password'
        }, {
            headers: { 'Content-Type': 'application/json' }
        });
        console.log('Status:', res.status);
        console.log('Cookies:', res.headers['set-cookie']);
        console.log('Data:', res.data);
    } catch (err) {
        console.error('Error status:', err.response?.status);
        console.error('Error data:', err.response?.data);
    }
}
test();
