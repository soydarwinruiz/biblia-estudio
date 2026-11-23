# Datos personales (Room + kotlinx.serialization para respaldos)
-keep class com.biblia.estudio.data.user.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.biblia.estudio.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.biblia.estudio.work.** { *; }
-dontwarn org.slf4j.**
