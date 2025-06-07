# WordContext AI

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Material%20Design-757575?style=for-the-badge&logo=material-design&logoColor=white" alt="Material Design">
  <img src="https://img.shields.io/badge/API-26%2B-brightgreen?style=for-the-badge" alt="API">
</p>

An intelligent vocabulary learning tool powered by AI that generates contextual articles containing target vocabulary words to help users master word usage in real-world scenarios.

## ✨ Features

### 🎯 Core Functionality
- **AI-Powered Article Generation**: Leverages DeepSeek AI to create contextual articles
- **Smart Word Highlighting**: Automatically highlights target vocabulary in generated content
- **Multiple Writing Styles**: Supports Academic, Daily, Business, and Literary writing styles
- **Bilingual Support**: Generate articles in both Chinese and English
- **Search History**: Save and manage your vocabulary learning history
- **User Authentication**: Secure login system with personalized experience

### 🎨 User Interface
- **Material Design 3**: Modern and intuitive user interface
- **Dark/Light Theme**: Adaptive theming support
- **Responsive Layout**: Optimized for various screen sizes
- **Smooth Animations**: Enhanced user experience with fluid transitions

### 🔧 Utility Features
- **One-Click Copy**: Easily copy generated articles to clipboard
- **Share Functionality**: Share articles across different platforms
- **Translation Support**: Built-in translation capabilities
- **Export Options**: Save articles for offline access

## 🏗️ Technical Architecture

### Architecture Pattern
- **MVVM (Model-View-ViewModel)**: Clean separation of concerns
- **Repository Pattern**: Centralized data management
- **Single Activity Architecture**: Modern Android navigation approach

### Tech Stack
- **Language**: Kotlin 100%
- **UI Framework**: Android Views with ViewBinding
- **Design System**: Material Design 3
- **Network**: Retrofit + OkHttp + Coroutines
- **Database**: Room Database
- **Async Processing**: Kotlin Coroutines + Flow
- **Image Loading**: Glide
- **Markdown Rendering**: Markwon

### Dependencies
```kotlin
// Core Android
implementation "androidx.core:core-ktx:1.12.0"
implementation "androidx.appcompat:appcompat:1.6.1"
implementation "com.google.android.material:material:1.11.0"

// Architecture Components
implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"
implementation "androidx.room:room-runtime:2.6.1"

// Network
implementation "com.squareup.retrofit2:retrofit:2.9.0"
implementation "com.squareup.okhttp3:logging-interceptor:4.12.0"

// Coroutines
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

// Markdown
implementation "io.noties.markwon:core:4.6.2"
```

## 📱 How to Use

### Getting Started
1. **Input Vocabulary**: Enter the word or phrase you want to learn in the input field
2. **Configure Settings**: Tap the settings button to choose article style and language preferences
3. **Generate Content**: Tap the send button to generate AI-powered contextual articles
4. **Study Vocabulary**: Target words are automatically highlighted for easy identification
5. **Save & Share**: Use copy or share functions to save your learning materials

### Feature Guide
- **Search History**: Access your previous searches from the history panel
- **Translation**: Use the translate button to get translations of generated content
- **User Profile**: Login to sync your data across devices
- **Customization**: Adjust article style and language in settings

## 🚀 Installation & Setup

### Prerequisites
- Android Studio 2023.1.1 or later
- Android SDK API level 26 or higher
- Java 11
- Internet connection for AI functionality

### Build Instructions
1. Clone the repository
   ```bash
   git clone https://github.com/songsize/WordContextAI.git
   ```

2. Open in Android Studio
   ```bash
   cd WordContextAI
   # Open project in Android Studio
   ```

3. Configure API Keys
   - Set up your DeepSeek AI API key in the app settings
   - Configure any additional API endpoints as needed

4. Build and Run
   ```bash
   ./gradlew assembleDebug
   # Or use Android Studio's build system
   ```

## 📁 Project Structure

```
app/src/main/java/com/wordcontextai/
├── adapter/           # RecyclerView adapters
├── data/             # Data models and entities
├── network/          # API services and networking
├── repository/       # Data repository layer
├── utils/           # Utility classes and helpers
├── viewmodel/       # ViewModel classes
├── LoginActivity.kt  # Authentication activity
└── MainActivity.kt   # Main application activity

app/src/main/res/
├── drawable/        # Vector drawables and icons
├── layout/         # XML layout files
├── values/         # Colors, strings, styles
└── ...
```

## 🔧 Configuration

### API Setup
1. Obtain a DeepSeek AI API key from their official website
2. Configure the API key in the app settings or preferences
3. Ensure network permissions are properly set

### Customization
- Modify themes in `res/values/themes.xml`
- Adjust colors in `res/values/colors.xml`
- Configure app behavior in `UserPreferences.kt`

## 🤝 Contributing

We welcome contributions! Here's how you can help:

1. **Fork the repository**
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Commit your changes** (`git commit -m 'Add amazing feature'`)
4. **Push to the branch** (`git push origin feature/amazing-feature`)
5. **Open a Pull Request**

### Development Guidelines
- Follow Kotlin coding conventions
- Use meaningful commit messages
- Add documentation for new features
- Test your changes thoroughly

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **DeepSeek AI** for providing the language model API
- **Material Design** for the design system
- **Android Jetpack** for the architecture components
- **Open Source Community** for the excellent libraries

