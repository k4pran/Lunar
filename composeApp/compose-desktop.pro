# ProGuard rules for the desktop release build (createReleaseDistributable).

# PDFBox renders library thumbnails. It references optional dependencies
# (BouncyCastle for encrypted PDFs, alternate commons-logging backends) that
# are not on the classpath, and loads fonts/codecs reflectively.
-keep class org.apache.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-keep class org.apache.commons.logging.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.apache.avalon.framework.**
-dontwarn org.apache.log.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn javax.servlet.**
-dontwarn org.apache.commons.logging.impl.**
# Java 9+ MethodHandle signature-polymorphic call in PDFBox's IOUtils.
-dontwarn org.apache.pdfbox.io.IOUtils
