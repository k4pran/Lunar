# ProGuard rules for the Compose desktop release build (proguardReleaseJars).

# PDFBox ships optional integrations (Bouncy Castle encryption, alternate
# logging backends) that Lunar does not bundle. Ignore those references.
-dontwarn org.bouncycastle.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.log.**
-dontwarn org.apache.avalon.**
-dontwarn org.slf4j.**
-dontwarn javax.servlet.**
# IOUtils calls the signature-polymorphic MethodHandle.invokeExact, which
# ProGuard cannot resolve against the library class.
-dontwarn org.apache.pdfbox.io.IOUtils
# Optional log4j adapter inside commons-logging; log4j is not bundled.
-dontwarn org.apache.commons.logging.impl.Log4jApiLogFactory**

# PDFBox/FontBox and commons-logging load implementations and resources
# reflectively, so keep them intact.
-keep class org.apache.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-keep class org.apache.commons.logging.** { *; }
