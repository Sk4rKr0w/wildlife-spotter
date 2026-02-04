# Wildlife Spotter

Wildlife Spotter is an Android app for logging wildlife sightings. It is meant to get you outside for walks in nature, then turn those walks into wildlife spotting. You can take a photo, add a short description, and save a spot with location data. The app also shows your spots on a map and basic activity stats.

## Features
1. Add a spot with photo, notes, and your location.
2. View nearby spots on a map.
3. See your activity stats (steps and total spots).
4. Compete with people from all over the world.

## Architecture Overview
The project is split into an Android frontend and a Node.js backend. Firebase is used for authentication and most app data.

### Android
1. Kotlin + Jetpack Compose UI.
2. ViewModels call DataSources for Firestore, storage, and network work.
3. Firebase Auth handles login; Firestore stores user data and spots.
4. Retrofit is used to call the backend for image upload and identification.
5. Animals in images are identified using an external API.

### Backend (Node.js)
1. Express server with a small REST API.
2. Firebase Admin verifies auth tokens.
3. Images are stored on disk and indexed in MySQL.
4. Image identification is proxied to an external detection API.

## Project Structure
1. `frontend/` Android app.
2. `backend/` Node.js API + MySQL + image storage.
