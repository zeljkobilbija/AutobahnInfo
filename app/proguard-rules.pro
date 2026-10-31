# WorkManager (dolazi uz AdMob) pravi svoju bazu preko refleksije:
# bez ovoga R8 izbaci konstruktor WorkDatabase_Impl i aplikacija pada pri pokretanju.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
