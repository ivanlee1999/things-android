# Minification is off (the app is small and the APK is installed by hand), so this file only
# carries rules that must survive turning it on later.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class us.liyifan.things.**$$serializer { *; }
-keepclassmembers class us.liyifan.things.** { *** Companion; }
