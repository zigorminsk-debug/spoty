# Минификация в релизе выключена (см. app/build.gradle.kts), правила — на будущее.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class io.github.zigorminsk.spoty.** { kotlinx.serialization.KSerializer serializer(...); }
