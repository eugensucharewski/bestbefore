# Keep Firestore and Serialization models
-keepclassmembers class de.eugens.bestbefore.products.domain.model.** { *; }
-keepclassmembers class de.eugens.bestbefore.auth.presentation.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, ElementValuePairs, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    public static ** INSTANCE;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Firebase Firestore reflection rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
}

# WorkManager Hilt
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
