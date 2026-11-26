# kotlinx.serialization keeps generated serializers reachable via companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class com.motivbot.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.motivbot.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
