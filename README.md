# BestBefore
BestBefore is an Android application designed to help you keep track of your products' shelf life and reduce food waste. With this app, you can easily manage your inventory, monitor expiration dates, and receive timely notifications before your items go bad.
<br>
<br>
<br>
🚀 Features  

• Product Management: Add and organize products with their names and expiration dates.  
• Expiration Tracking: Visual indicators for products that are expiring soon or have already expired.  
• Automated Notifications: Background workers (WorkManager) check for expiring products and send alerts to your device.   
• Cloud Sync: Powered by Firebase, ensuring your data is securely stored and accessible across your devices.  
• Secure Authentication: User accounts with Sign-In and Sign-Up functionality using Firebase Auth.  
• Modern UI: Built entirely with Jetpack Compose for a reactive and smooth user experience.  
• Clean Architecture: The project follows Clean Architecture principles (Data, Domain, and Presentation layers) for maintainability and scalability.  
<br>
<br>
## ⚖️ Legal Disclaimer

**This project is for educational and portfolio purposes only.**

### Health & Safety Warning
*   **Not a Professional Tool:** This application is a reference implementation and should not be used as the primary method for ensuring food safety.
*   **Manual Inspection Required:** Always manually inspect food products for signs of spoilage, smell, and texture before consumption, regardless of the dates shown in the app.
*   **No Liability:** The developer assumes no responsibility for any illness, injury, or loss resulting from the use of this software, reliance on its notifications, or inaccuracies in the data provided.

### Data Privacy & Security
*   **User-Managed Backend:** This application does not come with a centralized backend. To use the app, you must configure your own Firebase instance.
*   **No Data Collection:** The developer does *not* collect, store, or have access to any data entered into the application. All information (emails, product names, dates) is stored exclusively in the user's personal Firebase project.
*   **Security Responsibility:** Users are solely responsible for the security, costs, and data management of their own Firebase account.

### License
This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.

---

🛠 Tech Stack  

• Language: Kotlin  
• UI Framework: Jetpack Compose  
• Dependency Injection: Dagger Hilt  
• Backend: Firebase (Firestore, Authentication)  
• Background Tasks: WorkManager  
• Navigation: Jetpack Navigation  
<br>
<br>
⚠️ Important Note  

This project was developed for personal use. As it relies on Firebase services, it will not work out of the box without proper configuration.  
To run this project, you must use your own Firebase account:  

1. Create a new project in the Firebase Console.  
2. Enable Email/Password Authentication.  
3. Set up a Cloud Firestore database.  
4. Download the google-services.json file for your project and place it in the app/ directory of this repository.  
