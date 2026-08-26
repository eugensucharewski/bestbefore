# bestbefore
BestBefore is an Android application designed to help you keep track of your products' shelf life and reduce food waste. With this app, you can easily manage your inventory, monitor expiration dates, and receive timely notifications before your items go bad.

🚀 Features
• Product Management: Add and organize products with their names and expiration dates.
• Expiration Tracking: Visual indicators for products that are expiring soon or have already expired.
• Automated Notifications: Background workers (WorkManager) check for expiring products and send alerts to your device.
• Cloud Sync: Powered by Firebase, ensuring your data is securely stored and accessible across your devices.
• Secure Authentication: User accounts with Sign-In and Sign-Up functionality using Firebase Auth.
• Modern UI: Built entirely with Jetpack Compose for a reactive and smooth user experience.
• Clean Architecture: The project follows Clean Architecture principles (Data, Domain, and Presentation layers) for maintainability and scalability.

🛠 Tech Stack
• Language: Kotlin
• UI Framework: Jetpack Compose
• Dependency Injection: Dagger Hilt
• Backend: Firebase (Firestore, Authentication)
• Background Tasks: WorkManager
• Navigation: Jetpack Navigation

⚠️ Important Note
This project was developed for personal use. As it relies on Firebase services, it will not work out of the box without proper configuration.
To run this project, you must use your own Firebase account:
1. Create a new project in the Firebase Console.
2. Enable Email/Password Authentication.
3. Set up a Cloud Firestore database.
4. Download the google-services.json file for your project and place it in the app/ directory of this repository.
