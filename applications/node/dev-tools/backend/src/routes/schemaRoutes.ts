import { Router } from 'express'
import { ProtobufSchemaLoader } from '@pipeline/protobuf-forms'
import path from 'path'

const router = Router()

// Cache for loaded schemas
const schemaCache = new Map<string, any>()

// Get schema for a protobuf message type
router.get('/proto/:messageType', async (req, res) => {
  const { messageType } = req.params
  
  try {
    // Check cache first
    const cacheKey = `proto:${messageType}`
    if (schemaCache.has(cacheKey)) {
      return res.json(schemaCache.get(cacheKey))
    }
    
    // Create loader
    const loader = new ProtobufSchemaLoader({
      addUiHints: true,
      includeComments: true
    })
    
    // Load the proto file
    const protoPath = path.join(__dirname, '../../proto/pipeline_core_types.proto')
    await loader.loadProtoFile(protoPath)
    
    // Get schema for the message type
    const schema = loader.getMessageSchema(messageType)
    
    // Cache it
    schemaCache.set(cacheKey, schema)
    
    res.json(schema)
  } catch (error) {
    console.error(`Error loading schema for ${messageType}:`, error)
    res.status(500).json({ 
      error: 'Failed to load schema',
      details: (error as Error).message 
    })
  }
})

// Get all available message types
router.get('/proto', async (req, res) => {
  try {
    const loader = new ProtobufSchemaLoader()
    const protoPath = path.join(__dirname, '../../proto/pipeline_core_types.proto')
    await loader.loadProtoFile(protoPath)
    
    const schemas = loader.getAllMessageSchemas()
    const messageTypes = Object.keys(schemas)
    
    res.json({ messageTypes })
  } catch (error) {
    console.error('Error loading message types:', error)
    res.status(500).json({ 
      error: 'Failed to load message types',
      details: (error as Error).message 
    })
  }
})

export default router