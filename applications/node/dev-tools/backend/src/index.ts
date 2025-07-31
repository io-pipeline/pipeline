import express from 'express';
import cors from 'cors';
import { storageService } from './services/storageService';
import { expressConnectMiddleware } from "@connectrpc/connect-express";
import connectRoutes from './routes/connectRoutes';

const app = express();
const PORT = process.env.PORT || 3000;



// Middleware
app.use(cors());



// Routes




app.get('/health', (req, res) => {
    res.json({ status: 'healthy', timestamp: new Date().toISOString() });
});



// Mount Connect routes FIRST (before JSON middleware)
app.use(
    expressConnectMiddleware({
        routes: connectRoutes,
        requestPathPrefix: "/connect",
    })
);

// All endpoints now use Connect protocol
// No need for JSON middleware since Connect handles its own serialization

// Initialize and start server
async function startServer() {
    try {
        // Initialize storage service
        await storageService.initialize();
        
        app.listen(PORT, () => {
            console.log(`Developer tool backend listening at http://localhost:${PORT}`);
            console.log(`Connect API available at: http://localhost:${PORT}/connect`);
            console.log(`Storage: Local file-based`);
        });
    } catch (error) {
        console.error('Failed to start server:', error);
        process.exit(1);
    }
}

// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\nShutting down gracefully...');
    process.exit(0);
});

startServer();