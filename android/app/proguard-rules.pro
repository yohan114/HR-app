# kotlinx.serialization keeps generated serializers via annotations.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hr.**$$serializer { *; }
-keepclassmembers class com.hr.** { *** Companion; }
-keepclasseswithmembers class com.hr.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit interfaces are consumed reflectively.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# SQLCipher loads native code.
-keep class net.zetetic.database.** { *; }
