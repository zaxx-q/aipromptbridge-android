# ProGuard / R8 rules for AIPromptBridge

# Kotlin Serialization
# Keep Serializer classes and Companion for @Serializable data classes
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    *** INSTANCE;
}

# Android Keystore Secret Cipher
-dontwarn com.aipromptbridge.data.KeystoreSecretCipher
