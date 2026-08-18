# FixMate Android

Native Android app for public home-service bookings such as electrician, AC service, plumbing, appliance repair, cleaning, and carpentry.

## Open In Android Studio

1. Open Android Studio.
2. Choose `Open`.
3. Select this exact folder: `fixmate-android`.
4. Do not open the parent `New project` folder, because it contains many unrelated projects.
5. Wait for Gradle sync.
6. Run the `app` configuration on an emulator or phone.

The project pins Gradle Java to Android Studio's bundled JBR at:

`C:/Program Files/Android/Android Studio/jbr`

You can also double-click `open-in-android-studio.bat` from this folder.

## Port Repair

If Android Studio shows a port error:

1. Close the emulator/app run window.
2. Double-click `repair-ports-and-build.bat`.
3. Open Android Studio again with this folder only.

The repair script frees Metro/Expo port `8081`, closes stale emulator processes, restarts ADB on `5037`, and rebuilds the Android app.

## Current Flow

- Home header with city and trust signal.
- Service categories.
- Dynamic service cards.
- Bottom booking estimate bar.
- Booking dialog with customer details, address, issue notes, and preferred slot.
- Local booking confirmation ID.
