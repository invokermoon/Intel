
# This makes it easier to autocomplete methods in an IDE using this JAR.
-keepparameternames

# Ensure stack traces are reversible.
-renamesourcefileattribute SourceFile
-keepattributes LineNumberTable,SourceFile

# Keeping EnclosingMethods avoids mostly harmless warnings when compiling clients of the
# library, while InnerClass is important to ensure the resultant library functions properly.
-keepattributes EnclosingMethod,InnerClasses,Exceptions,Signature

# Keep the public classes of the library.
-keep public class com.intel.** { public protected *; }

# Apache
-dontwarn org.apache.**

# Work around Android injecting -keeppackagenames for library project builds.
-keeppackagenames !**


