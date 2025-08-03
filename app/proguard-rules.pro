# Add project specific ProGuard rules here

# Keep all model classes for Room and Retrofit
-keep class com.rgbc.cloudBackup.core.data.database.entity.** { *; }
-keep class com.rgbc.cloudBackup.core.data.network.dto.** { *; }

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*

-dontwarn sun.misc.**
-keep class com.google.gson.examples.android.model.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Security and Encryption
-keep class androidx.security.crypto.** { *; }
-keep class net.zetetic.database.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.InputMerger
-keep class androidx.work.impl.background.systemalarm.RescheduleReceiver

-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**
