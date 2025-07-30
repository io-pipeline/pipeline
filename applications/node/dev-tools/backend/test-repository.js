const axios = require('axios');

async function testRepository() {
    const baseUrl = 'http://localhost:3000/api/repository';
    
    try {
        console.log('1. Testing document creation...');
        const createResponse = await axios.post(`${baseUrl}/test-create`);
        console.log('✓ Document created:', createResponse.data);
        
        const storageId = createResponse.data.storageId;
        console.log('  Storage ID:', storageId);
        
        console.log('\n2. Testing document retrieval...');
        const getResponse = await axios.get(`${baseUrl}/${storageId}`);
        console.log('✓ Document retrieved:', getResponse.data.document.document.title);
        
        console.log('\n3. Testing document listing...');
        const listResponse = await axios.get(`${baseUrl}/list?pageSize=5`);
        console.log('✓ Documents listed:', listResponse.data.documents.length, 'documents');
        console.log('  Total count:', listResponse.data.totalCount);
        
        console.log('\n4. Testing document deletion...');
        const deleteResponse = await axios.delete(`${baseUrl}/${storageId}`);
        console.log('✓ Document deleted:', deleteResponse.data.message);
        
        console.log('\n✅ All tests passed!');
    } catch (error) {
        console.error('❌ Test failed:', error.response?.data || error.message);
    }
}

testRepository();