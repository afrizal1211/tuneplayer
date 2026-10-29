-keepattributes *Annotation*, InnerClasses
-dontwarn org.slf4j.**
-dontwarn kotlinx.serialization.**

# kotlinx.serialization: generated serializers are looked up reflectively.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.tuneplayer.app.**$$serializer { *; }
-keepclassmembers class com.tuneplayer.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.tuneplayer.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# innertubex models are decoded via kotlinx.serialization from network JSON.
-keepclassmembers class com.metrolist.innertubex.** {
    *** Companion;
}
-keepclasseswithmembers class com.metrolist.innertubex.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 session / notification resurface via reflection.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp platform probes optional Android classes at runtime.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room entity/DAO names are used in generated code and schema validation.
-keep class com.tuneplayer.app.data.db.** { *; }
