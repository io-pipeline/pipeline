# Database Status Indicators

The app bar now shows real-time status for Docker and MongoDB:

## Docker Icon (mdi-docker)
- 🟩 **Green**: Docker is available and MongoDB container is running
- 🟡 **Yellow**: Docker is available but no container is running
- ⚫ **Grey**: Docker is not available

## MongoDB Icon (mdi-database)
- 🟩 **Green**: Connected to MongoDB (shows storage type in tooltip)
- 🟡 **Yellow**: MongoDB available but not connected
- ⚫ **Grey**: Using local storage fallback

## Features:
1. **Clickable navigation**: Click either icon to go directly to Admin tab
2. **Real-time updates**: Status refreshes every 5 seconds
3. **Tooltips**: Hover over icons to see detailed status and "Click for settings"
4. **Visual feedback**: Colors indicate connection state at a glance
5. **Storage type indicator**: Shows if using MongoDB or localStorage

## Implementation:
- Global database store tracks status across all components
- MongoDBConfig component in Admin tab for configuration
- Auto-detection of Docker and MongoDB availability
- Seamless fallback to localStorage when MongoDB unavailable